package local.muse.patches.youtube.statusbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import local.muse.patches.youtube.YtConstants.COMPATIBILITY_YOUTUBE
import local.muse.utils.extendWithBundled

/**
 * Called by any list when it scrolls, before its scroll listeners are called.
 * The same fingerprint Morphe's Navigation bar patch uses: RecyclerView
 * .onScrolled(dx, dy), so p0 is the list and p2 is dy.
 */
internal object ListScrolledFingerprint : Fingerprint(
    definingClass = "Landroid/support/v7/widget/RecyclerView;",
    returnType = "V",
    parameters = listOf("I", "I"),
    filters = listOf(
        methodCall(smali = "Landroid/support/v7/widget/RecyclerView;->onScrollChanged(IIII)V")
    )
)

/**
 * Shows the view drawn behind the status bar and sets its color.
 * The same fingerprint Morphe's Hide status bar patch uses.
 */
internal object StatusBarBackgroundShowFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(),
        filters = listOf(
            methodCall(smali = "Landroid/view/View;->bringToFront()V"),
            methodCall(smali = "Landroid/view/View;->getParent()Landroid/view/ViewParent;"),
            methodCall(smali = "Landroid/view/View;->bringToFront()V")
        )
    ),
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(smali = "Landroid/view/View;->setVisibility(I)V"),
        methodCall(smali = "Landroid/view/View;->setBackgroundColor(I)V")
    )
)

private const val EXTENSION_CLASS = "Llocal/muse/extension/youtube/StatusBarScrollPatch;"

/**
 * Standalone patch: hides the system status bar while scrolling down,
 * together with the bottom navigation bar, and shows it again when
 * scrolling up. Always on (a standalone bundle has no in-app settings
 * screen); turn it off by not applying this patch.
 */
@Suppress("unused")
val hideStatusBarOnScrollPatch =
    bytecodePatch(
        name = "Hide status bar on scroll",
        description = "Hides the system status bar while scrolling down, together with the bottom navigation bar, and shows it again when scrolling up.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_YOUTUBE)

        extendWithBundled("extensions/muse-patches.dex")

        execute {
            ListScrolledFingerprint.method.addInstruction(
                0,
                "invoke-static { p0, p2 }, $EXTENSION_CLASS->onListScrolled(Landroid/view/View;I)V",
            )

            // The view drawn behind the status bar keeps the height of the
            // hidden status bar and stays visible as a translucent black bar,
            // so it is hidden together with the status bar.
            StatusBarBackgroundShowFingerprint.let {
                it.method.apply {
                    val index = it.instructionMatches.last().index
                    val viewRegister = getInstruction<FiveRegisterInstruction>(index).registerC

                    addInstruction(
                        index + 1,
                        "invoke-static { v$viewRegister }, $EXTENSION_CLASS->hideStatusBarBackground(Landroid/view/View;)V",
                    )
                }
            }
        }
    }
