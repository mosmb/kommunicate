package io.mosmb.library.scanner

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings

/**
 * Class used to build a [Scanner] following the defined parameters.
 */
class ScannerBuilder {

    var settings: ScanSettings = ScanSettings.Builder().build()

    var filters = emptyList<ScanFilter>()

    /**
     * Return an instance of [Scanner] built based on the parameters of this class.
     */
    fun build(): Scanner =
        ScannerImp(
            settings,
            filters
        )
}
