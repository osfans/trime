# SPDX-License-Identifier: GPL-3.0-or-later

# An unsigned minor version cannot be older than a zero minimum. Keep every
# descriptor check, and retain the original comparison for a positive minimum.
function(trime_adapt_opencc_plugin source)
  trime_require_dependency_text("${source}/plugin/OpenCCPlugin.h"
    "#define OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR 0\n")
  trime_require_dependency_text("${source}/plugin/OpenCCPlugin.h"
    "uint16_t abi_minor;")
  trime_replace_dependency_cmake("${source}/PluginSegmentation.cpp"
    [=[    if (descriptor->abi_minor < OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR) {
      throw Exception("Plugin ABI minor version is older than required by the host.");
    }]=]
    [=[#if OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR > 0
    if (descriptor->abi_minor < OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR) {
      throw Exception("Plugin ABI minor version is older than required by the host.");
    }
#endif]=])
endfunction()
