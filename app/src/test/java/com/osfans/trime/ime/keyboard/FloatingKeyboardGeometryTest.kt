/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import androidx.core.graphics.Insets
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FloatingKeyboardGeometryTest :
    BehaviorSpec({
        Given("a floating keyboard width resolver") {
            When("the percentage is in range") {
                Then("the width is a rounded fraction of the container") {
                    FloatingKeyboardGeometry.widthFor(1920, 75) shouldBe 1440
                    FloatingKeyboardGeometry.widthFor(1001, 75) shouldBe 751
                }
            }

            When("the percentage is too small") {
                Then("it is clamped to the minimum") {
                    FloatingKeyboardGeometry.widthFor(1920, 10) shouldBe 384
                }
            }

            When("the percentage is too large") {
                Then("it is clamped to the full width") {
                    FloatingKeyboardGeometry.widthFor(1920, 120) shouldBe 1920
                }
            }

            When("the container has no width") {
                Then("the result is zero") {
                    FloatingKeyboardGeometry.widthFor(0, 75) shouldBe 0
                }
            }

            When("a minimum width is requested") {
                Then("the card is not narrower than it") {
                    FloatingKeyboardGeometry.widthFor(1920, 40, minWidth = 800) shouldBe 800
                }
            }

            When("the minimum width does not fit") {
                Then("it is ignored") {
                    FloatingKeyboardGeometry.widthFor(500, 40, minWidth = 800) shouldBe 500
                }
            }

            When("the container is a large tablet") {
                Then("the card is not wider than a comfortable maximum") {
                    FloatingKeyboardGeometry.widthFor(
                        containerWidth = 2560,
                        percent = 50,
                        minWidth = 520,
                        maxWidth = 1440,
                    ) shouldBe 1280
                    FloatingKeyboardGeometry.widthFor(
                        containerWidth = 2560,
                        percent = 100,
                        minWidth = 520,
                        maxWidth = 1440,
                    ) shouldBe 1440
                }
            }

            When("the maximum width does not fit the container") {
                Then("the container is the limit") {
                    FloatingKeyboardGeometry.widthFor(
                        containerWidth = 1000,
                        percent = 100,
                        maxWidth = 1440,
                    ) shouldBe 1000
                }
            }

            When("the maximum width is narrower than the minimum") {
                Then("the maximum wins") {
                    FloatingKeyboardGeometry.widthFor(
                        containerWidth = 1000,
                        percent = 50,
                        minWidth = 520,
                        maxWidth = 400,
                    ) shouldBe 400
                }
            }
        }

        Given("a floating keyboard height budget") {
            When("the card mirrors a portrait keyboard without bottom padding") {
                Then("the handle and card padding come out of the key area") {
                    FloatingKeyboardGeometry.floatingKeyHeight(
                        portraitKeyboardHeight = 250,
                        portraitBottomPadding = 0,
                        dragHandleHeight = 32,
                        cardBottomPadding = 8,
                    ) shouldBe 210
                }
            }

            When("the portrait keyboard has bottom padding") {
                Then("the padding is part of the same budget") {
                    FloatingKeyboardGeometry.floatingKeyHeight(
                        portraitKeyboardHeight = 250,
                        portraitBottomPadding = 40,
                        dragHandleHeight = 32,
                        cardBottomPadding = 8,
                    ) shouldBe 250
                }
            }

            When("the budget is smaller than the fixed parts") {
                Then("the key area keeps a minimum height") {
                    FloatingKeyboardGeometry.floatingKeyHeight(
                        portraitKeyboardHeight = 20,
                        portraitBottomPadding = 0,
                        dragHandleHeight = 32,
                        cardBottomPadding = 8,
                    ) shouldBe 1
                }
            }

            When("the card is scaled down") {
                Then("the key area scales with the width") {
                    FloatingKeyboardGeometry.floatingKeyHeight(
                        portraitKeyboardHeight = 250,
                        portraitBottomPadding = 8,
                        dragHandleHeight = 32,
                        cardBottomPadding = 8,
                        scalePercent = 50,
                    ) shouldBe 109
                }
            }
        }

        Given("a default floating position") {
            When("the card starts against the right edge") {
                Then("a margin is left between the card and the edge") {
                    FloatingKeyboardGeometry.defaultPosition(
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 400,
                        margin = 24,
                        center = false,
                    ) shouldBe 576
                }
            }

            When("the card is centered in the safe area") {
                Then("the margins are included in the calculation") {
                    FloatingKeyboardGeometry.defaultPosition(
                        containerStart = 50,
                        containerEnd = 950,
                        viewSize = 400,
                        margin = 20,
                        center = true,
                    ) shouldBe 300
                }
            }

            When("the card does not fit the container") {
                Then("it is aligned to the start of the safe area") {
                    FloatingKeyboardGeometry.defaultPosition(
                        containerStart = 30,
                        containerEnd = 300,
                        viewSize = 500,
                        margin = 10,
                        center = false,
                    ) shouldBe 40
                }
            }

            When("the container is empty") {
                Then("the start position is returned") {
                    FloatingKeyboardGeometry.defaultPosition(
                        containerStart = 30,
                        containerEnd = 30,
                        viewSize = 100,
                        margin = 10,
                        center = true,
                    ) shouldBe 30
                }
            }
        }

        Given("a width percentage resolver") {
            When("the width is inside the range") {
                Then("it is converted to a percentage") {
                    FloatingKeyboardGeometry.percentFor(1000, 500) shouldBe 50
                    FloatingKeyboardGeometry.percentFor(1001, 500) shouldBe 50
                }
            }

            When("the width is below the minimum") {
                Then("the percentage is clamped") {
                    FloatingKeyboardGeometry.percentFor(1000, 100) shouldBe 20
                }
            }

            When("the width is above the container") {
                Then("the percentage is clamped") {
                    FloatingKeyboardGeometry.percentFor(1000, 1200) shouldBe 100
                }
            }

            When("the container or width is empty") {
                Then("the minimum percentage is used") {
                    FloatingKeyboardGeometry.percentFor(0, 500) shouldBe 20
                    FloatingKeyboardGeometry.percentFor(1000, 0) shouldBe 20
                }
            }
        }

        Given("a drag translation clamper") {
            When("the keyboard is dragged horizontally") {
                Then("the translation stays inside the available space") {
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = -200f,
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 750,
                        basePosition = 125,
                    ) shouldBe -125f
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = 200f,
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 750,
                        basePosition = 125,
                    ) shouldBe 125f
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = 50f,
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 750,
                        basePosition = 125,
                    ) shouldBe 50f
                }
            }

            When("the keyboard is dragged vertically from the bottom") {
                Then("it cannot leave the top or bottom edge") {
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = -600f,
                        containerStart = 0,
                        containerEnd = 800,
                        viewSize = 300,
                        basePosition = 500,
                    ) shouldBe -500f
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = 100f,
                        containerStart = 0,
                        containerEnd = 800,
                        viewSize = 300,
                        basePosition = 500,
                    ) shouldBe 0f
                }
            }

            When("the container is inset by system bars") {
                Then("the view stays inside the inset area") {
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = -600f,
                        containerStart = 50,
                        containerEnd = 900,
                        viewSize = 300,
                        basePosition = 500,
                    ) shouldBe -450f
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = 200f,
                        containerStart = 50,
                        containerEnd = 900,
                        viewSize = 300,
                        basePosition = 500,
                    ) shouldBe 100f
                }
            }

            When("the container or view is empty") {
                Then("no translation is allowed") {
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = 10f,
                        containerStart = 0,
                        containerEnd = 300,
                        viewSize = 0,
                        basePosition = 0,
                    ) shouldBe 0f
                    FloatingKeyboardGeometry.clampTranslation(
                        translation = 10f,
                        containerStart = 800,
                        containerEnd = 0,
                        viewSize = 500,
                        basePosition = 0,
                    ) shouldBe 0f
                }
            }
        }

        Given("a system bar inset merger") {
            When("all system bars are visible") {
                Then("the visible insets are kept") {
                    FloatingKeyboardGeometry.mergeSystemInsets(
                        current = Insets.of(120, 63, 0, 48),
                        stable = Insets.of(120, 63, 0, 48),
                    ) shouldBe Insets.of(120, 63, 0, 48)
                }
            }

            When("the status bar is hidden") {
                Then("the stable top inset is still reserved") {
                    FloatingKeyboardGeometry.mergeSystemInsets(
                        current = Insets.of(0, 0, 0, 48),
                        stable = Insets.of(0, 63, 0, 48),
                    ) shouldBe Insets.of(0, 63, 0, 48)
                }
            }

            When("the navigation bar is hidden") {
                Then("the bottom follows the current inset") {
                    FloatingKeyboardGeometry.mergeSystemInsets(
                        current = Insets.of(0, 63, 0, 0),
                        stable = Insets.of(0, 63, 0, 48),
                    ) shouldBe Insets.of(0, 63, 0, 0)
                }
            }

            When("a side navigation bar is hidden") {
                Then("the stable side inset is still reserved") {
                    FloatingKeyboardGeometry.mergeSystemInsets(
                        current = Insets.of(0, 63, 0, 48),
                        stable = Insets.of(120, 63, 0, 48),
                    ) shouldBe Insets.of(120, 63, 0, 48)
                }
            }
        }

        Given("a snapping position") {
            When("the card is dropped near the left edge") {
                Then("it snaps to the edge") {
                    FloatingKeyboardGeometry.snapTranslation(
                        translation = -100f,
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 400,
                        basePosition = 100,
                        threshold = 24,
                        includeCentre = true,
                    ) shouldBe -100f
                }
            }

            When("the card is dropped near the middle") {
                Then("it snaps to the middle") {
                    FloatingKeyboardGeometry.snapTranslation(
                        translation = 210f,
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 400,
                        basePosition = 100,
                        threshold = 24,
                        includeCentre = true,
                    ) shouldBe 200f
                }
            }

            When("the card is dropped near the right edge") {
                Then("it snaps to the edge") {
                    FloatingKeyboardGeometry.snapTranslation(
                        translation = 490f,
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 400,
                        basePosition = 100,
                        threshold = 24,
                        includeCentre = true,
                    ) shouldBe 500f
                }
            }

            When("the card is dropped away from every anchor") {
                Then("it stays where it is") {
                    FloatingKeyboardGeometry.snapTranslation(
                        translation = 350f,
                        containerStart = 0,
                        containerEnd = 1000,
                        viewSize = 400,
                        basePosition = 100,
                        threshold = 24,
                        includeCentre = true,
                    ) shouldBe 350f
                }
            }

            When("the card is dropped near the top edge") {
                Then("it snaps to the edge") {
                    FloatingKeyboardGeometry.snapTranslation(
                        translation = -480f,
                        containerStart = 0,
                        containerEnd = 800,
                        viewSize = 300,
                        basePosition = 500,
                        threshold = 24,
                    ) shouldBe -500f
                }
            }

            When("the card is dropped away from the top and bottom edges") {
                Then("it stays where it is") {
                    FloatingKeyboardGeometry.snapTranslation(
                        translation = -400f,
                        containerStart = 0,
                        containerEnd = 800,
                        viewSize = 300,
                        basePosition = 500,
                        threshold = 24,
                    ) shouldBe -400f
                }
            }

            When("the card is wider than the container") {
                Then("the position is kept instead of failing") {
                    FloatingKeyboardGeometry.snapTranslation(
                        translation = 42f,
                        containerStart = 0,
                        containerEnd = 300,
                        viewSize = 500,
                        basePosition = 0,
                        threshold = 24,
                        includeCentre = true,
                    ) shouldBe 42f
                }
            }
        }

        Given("an anchor search") {
            When("an anchor is within the threshold") {
                Then("the closest one is used") {
                    FloatingKeyboardGeometry.snapToNearest(
                        position = 105,
                        anchors = listOf(0, 100, 400),
                        threshold = 24,
                    ) shouldBe 100
                }
            }

            When("no anchor is within the threshold") {
                Then("the position is kept") {
                    FloatingKeyboardGeometry.snapToNearest(
                        position = 250,
                        anchors = listOf(0, 100, 400),
                        threshold = 24,
                    ) shouldBe 250
                }
            }
        }
    })
