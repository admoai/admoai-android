package com.admoai.sdk.interactive

import java.lang.ref.WeakReference

/**
 * The live [LeadFormController]s of one SDK instance: it bounds how many hidden forms are loaded
 * at once and closes every one when the user or the value provider changes. Holds weak references,
 * so it never keeps a form alive. The list is guarded so the app may change the user from any
 * thread; controllers themselves are only touched on the main thread.
 */
@OptIn(ExperimentalInteractiveExperienceApi::class)
internal class LeadFormRegistry(private val contract: BridgeContract = BridgeContract) {
    private val controllers = ArrayList<WeakReference<LeadFormController>>()

    fun register(controller: LeadFormController) = synchronized(controllers) {
        controllers.removeAll { it.get().let { c -> c == null || c.isDisposed } }
        controllers += WeakReference(controller)
        Unit
    }

    /** True when nothing was registered since the last [disposeAll] or [clear]. */
    fun isEmpty(): Boolean = synchronized(controllers) { controllers.isEmpty() }

    /** True when [controller] may load now: fewer hidden loaded forms than the contract allows. */
    fun mayPrepare(controller: LeadFormController): Boolean {
        val hidden = live().count { it !== controller && it.countsAsHiddenPrepared }
        return hidden < contract.maxHiddenPreparedForms
    }

    /** Disposes every live controller. */
    fun disposeAll() {
        val live = synchronized(controllers) {
            val copy = controllers.mapNotNull { it.get() }
            controllers.clear()
            copy
        }
        live.forEach { it.dispose() }
    }

    /** Forgets every controller without disposing them. */
    fun clear() = synchronized(controllers) { controllers.clear() }

    private fun live(): List<LeadFormController> = synchronized(controllers) { controllers.mapNotNull { it.get() } }
}
