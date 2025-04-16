package com.example.securewelcomepage.interfaces

interface IChallengeManager {
    fun startChallenge()
    fun stopChallenge()
    fun isCompleted(): Boolean
    fun registerCompletionListener(listener: IOnChallengeCompletedListener)
}