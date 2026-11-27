package local.muse.utils

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21t
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22t
import com.android.tools.smali.dexlib2.iface.reference.Reference

/**
 * Local replacements for the handful of app.morphe.util helpers (from the
 * patches library) that are not bundled in the Morphe Desktop jar.
 */

@Suppress("UNCHECKED_CAST")
fun <T : Reference> Instruction.getReference(): T? =
    (this as? ReferenceInstruction)?.reference as? T

val Instruction.registersUsed: List<Int>
    get() =
        when (this) {
            is Instruction21c -> listOf(registerA)
            is Instruction11n -> listOf(registerA)
            is Instruction22c -> listOf(registerA, registerB)
            is Instruction21t -> listOf(registerA)
            is Instruction22t -> listOf(registerA, registerB)
            else -> emptyList()
        }

fun MutableMethod.indexOfFirstInstruction(opcode: Opcode): Int =
    instructions.indexOfFirst { it.opcode == opcode }

fun MutableMethod.indexOfFirstInstructionReversedOrThrow(opcode: Opcode): Int {
    val index = instructions.indexOfLast { it.opcode == opcode }
    if (index < 0) throw PatchException("Could not find instruction with opcode: $opcode")
    return index
}

fun MutableMethod.indexOfFirstInstruction(
    startIndex: Int,
    opcode: Opcode,
): Int {
    val list = instructions
    for (i in startIndex until list.size) {
        if (list[i].opcode == opcode) return i
    }
    return -1
}

/**
 * Loads an extension dex bundled inside this .mpp through the bundle's own
 * class loader (the same idiom standalone patches such as Media URL Copy
 * use). The BytecodePatchBuilder.extendWith(String) overload does not
 * resolve bundle resources, which left extension classes unmatched.
 */
private object BundledResourceAnchor

fun app.morphe.patcher.patch.BytecodePatchBuilder.extendWithBundled(extensionFileName: String) {
    val classLoader = BundledResourceAnchor::class.java.classLoader
    extendWith(
        java.util.function.Supplier<java.io.InputStream> {
            classLoader.getResourceAsStream(extensionFileName)
        },
    )
}

fun MutableMethod.indexOfFirstInstructionOrThrow(opcode: Opcode): Int {
    val index = instructions.indexOfFirst { it.opcode == opcode }
    if (index < 0) throw PatchException("Could not find instruction with opcode: $opcode")
    return index
}
