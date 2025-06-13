package com.futo.platformplayer.fragment.mainactivity

import android.util.Log
import androidx.fragment.app.Fragment
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment

open class MainActivityFragment : Fragment() {
    protected val currentMain : MainFragment
        get() {
        isValidMainActivity();
        return (activity as MainActivity).fragCurrent;
    }

    fun closeSegment() {
        val a = activity
        if (a is MainActivity)
            return a.closeSegment()
        else
            Log.e(TAG, "Failed to close segment due to activity not being a main activity.")
    }

    fun navigate(frag: MainFragment, parameter: Any? = null, withHistory: Boolean = true) {
        val a = activity
        if (a is MainActivity)
            (activity as MainActivity).navigate(frag, parameter, withHistory, false)
        else
            Log.e(TAG, "Failed to navigate due to activity not being a main activity.")
    }

    inline fun <reified T : MainFragment> navigate(parameter: Any? = null, withHistory: Boolean = true): T {
        val target = requireFragment<T>();
        navigate(target, parameter, withHistory);
        return target;
    }

    inline fun <reified T : Fragment> requireFragment() : T {
        isValidMainActivity();
        return (activity as MainActivity).getFragment<T>();
    }

    fun isValidMainActivity(){
        if(activity == null)
            throw java.lang.IllegalStateException("Attempted to use fragment without an activity");
        if(!(activity is MainActivity))
            throw java.lang.IllegalStateException("Attempted to use fragment without a MainActivty");
    }

    companion object {
        private const val TAG = "MainActivityFragment"
    }
}