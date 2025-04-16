package com.example.securewelcomepage.interfaces

import com.example.securewelcomepage.utilities.ChallengeType

interface IOnChallengeCompletedListener {
    fun onChallengeCompleted(challengeType: ChallengeType)
}