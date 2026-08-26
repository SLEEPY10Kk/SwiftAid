package com.example.swiftaid

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class SwiftAidInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "Call added: state=${call.state}")
    }

    override fun onCallRemoved(call: Call) {
        Log.i(TAG, "Call removed: state=${call.state}")
        super.onCallRemoved(call)
    }

    companion object {
        private const val TAG = "SwiftAidInCallService"
    }
}
