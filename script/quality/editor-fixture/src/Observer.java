/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.x2m7.trime.editorobserver;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.FileOutputStream;
import org.xmlpull.v1.XmlSerializer;

/** Read all accessible windows without restarting either the IME or its editor. */
@SuppressWarnings("deprecation")
public final class Observer extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    private static String value(CharSequence text) { return text == null ? "" : text.toString(); }

    private static void node(XmlSerializer xml, AccessibilityNodeInfo item) throws Exception {
        Rect bounds = new Rect();
        item.getBoundsInScreen(bounds);
        xml.startTag(null, "node");
        xml.attribute(null, "text", value(item.getText()));
        xml.attribute(null, "resource-id", value(item.getViewIdResourceName()));
        xml.attribute(null, "content-desc", value(item.getContentDescription()));
        xml.attribute(null, "package", value(item.getPackageName()));
        xml.attribute(null, "class", value(item.getClassName()));
        xml.attribute(null, "enabled", Boolean.toString(item.isEnabled()));
        xml.attribute(null, "password", Boolean.toString(item.isPassword()));
        xml.attribute(null, "bounds", bounds.toShortString());
        for (int i = 0; i < item.getChildCount(); i++) {
            AccessibilityNodeInfo child = item.getChild(i);
            if (child != null) {
                try { if (child.isVisibleToUser()) node(xml, child); }
                finally { child.recycle(); }
            }
        }
        xml.endTag(null, "node");
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean passed = false;
        File output = new File(getTargetContext().getCacheDir(), "windows.xml");
        if (output.exists() && !output.delete()) throw new IllegalStateException("Stale capture");
        try {
            if (!Build.HARDWARE.equals("ranchu") && !Build.HARDWARE.equals("goldfish")) throw new IllegalStateException("Emulator only");
            UiAutomation automation = getUiAutomation();
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            Thread.sleep(250);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                XmlSerializer xml = Xml.newSerializer();
                xml.setOutput(stream, "UTF-8");
                xml.startDocument("UTF-8", true);
                xml.startTag(null, "hierarchy");
                int count = 0;
                for (AccessibilityWindowInfo window : automation.getWindows()) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null) {
                        xml.startTag(null, "window");
                        xml.attribute(null, "type", Integer.toString(window.getType()));
                        try { node(xml, root); } finally { root.recycle(); }
                        xml.endTag(null, "window");
                        count++;
                    }
                    window.recycle();
                }
                xml.endTag(null, "hierarchy");
                xml.endDocument();
                if (count == 0) throw new IllegalStateException("No accessible windows");
                result.putInt("windows", count);
            }
            passed = true;
        } catch (Exception error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
        }
        result.putBoolean("passed", passed);
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
