package io.github.yanniboi.freeze

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Invisible activity that forwards one action to [FreezeService] and closes. */
class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_ACTION)?.let { FreezeService.send(this, it) }
        finish()
    }

    companion object {
        private const val EXTRA_ACTION = "action"

        fun intent(context: Context, action: String): Intent =
            Intent(context, TrampolineActivity::class.java).putExtra(EXTRA_ACTION, action)
    }
}
