package com.example.helloworldapp.utils

/**
 * Interface to be implemented by activities that need to be notified
 * when online/offline status changes
 */
interface OnlineStatusChangeListener {
    /**
     * Called when online/offline status has changed
     * @param isOnline The new status (true = online, false = offline)
     */
    fun onOnlineStatusChanged(isOnline: Boolean)
}