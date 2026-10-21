package com.adamglin.compose.markdown.core.engine

import com.adamglin.compose.markdown.core.internal.LinkReferenceDefinition

/**
 * The link reference definitions in effect: definitions in the stable prefix are final, definitions in
 * the mutable tail are replaced on every parse. As in CommonMark the first definition of a label wins,
 * and stable definitions always precede tail ones.
 */
internal class ReferenceDefinitions {
    private val stable = HashMap<String, LinkReferenceDefinition>()
    private var tail: Map<String, LinkReferenceDefinition> = emptyMap()

    /** Label to definition in effect; the inline parser reads it. */
    val effective: MutableMap<String, LinkReferenceDefinition> = HashMap()

    /** Applies one parse and returns the labels whose destination or title in effect changed. */
    fun update(
        newlyStable: Collection<LinkReferenceDefinition>,
        newTail: Map<String, LinkReferenceDefinition>,
    ): Set<String> {
        if (newlyStable.isEmpty() && tail.isEmpty() && newTail.isEmpty()) {
            return emptySet()
        }
        val candidates = HashSet<String>(tail.keys)
        candidates += newTail.keys
        newlyStable.forEach { definition ->
            candidates += definition.label
            if (definition.label !in stable) {
                stable[definition.label] = definition
            }
        }
        tail = newTail
        val changed = HashSet<String>()
        candidates.forEach { label ->
            val previous = effective[label]
            val current = stable[label] ?: tail[label]
            if (current == null) {
                effective.remove(label)
            } else {
                effective[label] = current
            }
            if (previous?.destination != current?.destination || previous?.title != current?.title || (previous == null) != (current == null)) {
                changed += label
            }
        }
        return changed
    }

    fun clear() {
        stable.clear()
        tail = emptyMap()
        effective.clear()
    }
}
