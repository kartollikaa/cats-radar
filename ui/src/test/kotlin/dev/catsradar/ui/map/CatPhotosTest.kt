package dev.catsradar.ui.map

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.compose.expressions.ast.CompiledExpression
import org.maplibre.compose.expressions.ast.CompiledFunctionCall
import org.maplibre.compose.expressions.ast.ExpressionContext

class CatPhotosTest {

    @Test
    fun theTermThatLaysTilesOutAgainReadsAFeatureAndChangesWithEveryRound() {
        val first = layOutAgainAfter(tileRound = 1).compile(ExpressionContext.None)
        val second = layOutAgainAfter(tileRound = 2).compile(ExpressionContext.None)

        assertTrue("reads no feature: $first", "get" in first.callNames())
        assertNotEquals(first, second)
    }

    private fun CompiledExpression<*>.callNames(): Set<String> {
        val call = this as? CompiledFunctionCall ?: return emptySet()
        return setOf(call.name) + call.args.flatMap { it.callNames() }
    }
}
