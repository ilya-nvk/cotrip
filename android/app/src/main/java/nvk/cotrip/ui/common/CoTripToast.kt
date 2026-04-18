package nvk.cotrip.ui.common

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun Context.showCoTripToast(@StringRes resId: Int) {
    Toast.makeText(this, getString(resId), Toast.LENGTH_LONG).show()
}
