package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Separate, user-approved accessibility service for UniTVnet integration.
 * Never changes the TV's Home role or remote-button mapping.
 * Only processes events originating from com.unitvnet.tvod.
 */
public final class UniChannelService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !"com.unitvnet.tvod".contentEquals(event.getPackageName())) return;
        PendingUniSearch.tryFill(this, event);
        PendingUniChannel.inspect(this, event);
    }
    @Override public void onInterrupt() {}
}
