package com.admoai.sdk.interactive

import java.lang.ref.WeakReference

/**
 * The live [LeadFormController]s of one SDK instance: it bounds how many hidden forms are loaded
 * at once and closes every one when the user or the value provider changes. Holds weak references,
 * so it never keeps a form alive. The list is guarded so the app may change the user from any
 * thread; controllers themselves are only touched on the main thread.
 *
 * It also owns the lead-form generation: a counter every user change and opt-out advances
 * synchronously, on the calling thread, before any cleanup is posted. A controller keeps the
 * generation it was created under and stops looking up or sending values once it is stale, so
 * nothing of the old user is released while the cleanup waits for the main thread.
 */
internal class LeadFormRegistry(private val contract: BridgeContract = BridgeContract) {
    private val controllers = ArrayList<WeakReference<LeadFormController>>()

    private var generation = 0L

    /** The generation a controller created now belongs to. */
    fun currentGeneration(): Long = synchronized(controllers) { generation }

    /** True while [generation] is the current one: no user change or opt-out happened since. */
    fun isCurrent(generation: Long): Boolean = synchronized(controllers) { generation == this.generation }

    /**
     * Keeps [controller] and returns true only when it was created under the current [generation];
     * otherwise returns false and keeps nothing: the caller disposes it.
     */
    fun register(controller: LeadFormController, generation: Long): Boolean = synchronized(controllers) {
        if (generation != this.generation) return@synchronized false
        controllers.removeAll { it.get().let { c -> c == null || c.isDisposed } }
        controllers += WeakReference(controller)
        true
    }

    /** True when [controller] may load now: fewer hidden loaded forms than the contract allows. */
    fun mayPrepare(controller: LeadFormController): Boolean {
        val hidden = live().count { it !== controller && it.countsAsHiddenPrepared }
        return hidden < contract.maxHiddenPreparedForms
    }

    /**
     * Advances the generation and detaches every live controller, in one step under the lock, and
     * returns them for the caller to dispose on the main thread. A controller registered afterwards
     * belongs to the new generation and is not among them.
     */
    fun invalidateAll(): List<LeadFormController> = synchronized(controllers) {
        generation++
        val detached = controllers.mapNotNull { it.get() }
        controllers.clear()
        detached
    }

    /** Forgets every controller without disposing them. */
    fun clear() = synchronized(controllers) { controllers.clear() }

    private fun live(): List<LeadFormController> = synchronized(controllers) { controllers.mapNotNull { it.get() } }
}
