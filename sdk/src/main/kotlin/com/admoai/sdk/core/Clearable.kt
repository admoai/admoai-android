package com.admoai.sdk.core
/**
 * Interface for objects that can be reset to default values or cleared.
 * @param T The type of the object that implements this interface.
 */
interface Clearable<T> {
    /**
     * Returns a new instance with default values.
     */
    fun resetToDefaults(): T

    /**
     * Returns a new instance with all optional fields cleared (typically set to null or empty).
     * Essential or required fields might retain their values or be set to a sensible cleared state.
     */
    fun clear(): T
}