import Foundation
import IOKit.pwr_mgt

/// Keeps the Mac from idle-sleeping while a sit runs (the macOS analog of the Android app's
/// partial wake lock) so the gong actually rings at zero. Held only during a sit; lid-close
/// still sleeps the machine — `SitTimer`'s wall-clock elapsed self-corrects on wake.
final class PowerAssertion {
    private var assertionID: IOPMAssertionID = 0
    private var active = false

    func begin(reason: String) {
        guard !active else { return }
        let result = IOPMAssertionCreateWithName(
            kIOPMAssertionTypePreventUserIdleSystemSleep as CFString,
            IOPMAssertionLevel(kIOPMAssertionLevelOn),
            reason as CFString,
            &assertionID
        )
        active = result == kIOReturnSuccess
    }

    func release() {
        guard active else { return }
        IOPMAssertionRelease(assertionID)
        active = false
    }

    deinit { release() }
}
