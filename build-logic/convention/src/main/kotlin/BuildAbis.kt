// SPDX-License-Identifier: GPL-3.0-or-later

/** Validate selected native ABIs without coupling builds to a release repository or signer. */
fun resolveBuildAbis(
    abiOverride: String?,
    defaults: List<String>,
): List<String> = (abiOverride?.split(',') ?: defaults).also { abis ->
    require(abis.isNotEmpty() && abis.distinct().size == abis.size && abis.all { it in Versions.supportedAbis }) {
        "BUILD_ABI/buildABI must contain distinct supported ABI names"
    }
}
