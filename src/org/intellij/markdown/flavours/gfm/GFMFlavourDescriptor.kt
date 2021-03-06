package org.intellij.markdown.flavours.gfm

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getParentOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.gfm.lexer._GFMLexer
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.html.TrimmingInlineHolderProvider
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl.*
import java.io.Reader
import java.net.URI

open class GFMFlavourDescriptor : CommonMarkFlavourDescriptor() {
    override val markerProcessorFactory = GFMMarkerProcessor.Factory

    override fun createInlinesLexer(): MarkdownLexer {
        return MarkdownLexer(_GFMLexer(null as Reader?))
    }

    override val sequentialParserManager = object : SequentialParserManager() {
        override fun getParserSequence(): List<SequentialParser> {
            return listOf(AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
                    BacktickParser(),
                    ImageParser(),
                    InlineLinkParser(),
                    ReferenceLinkParser(),
                    StrikeThroughParser(),
                    EmphStrongParser())
        }
    }

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
        return super.createHtmlGeneratingProviders(linkMap, baseURI) + hashMapOf(
                GFMElementTypes.STRIKETHROUGH to object: SimpleInlineTagProvider("span", 2, -2) {
                    override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
                        visitor.consumeTagOpen(node, tagName, "class=\"user-del\"")
                    }
                },

                GFMElementTypes.TABLE to TablesGeneratingProvider(),

                GFMTokenTypes.CELL to TrimmingInlineHolderProvider(),

                GFMTokenTypes.GFM_AUTOLINK to object : GeneratingProvider {
                    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
                        val linkText = node.getTextInNode(text)

                        // #28: do not render GFM autolinks under link titles
                        // (though it's "OK" according to CommonMark spec)
                        if (node.getParentOfType(MarkdownElementTypes.LINK_LABEL,
                                        MarkdownElementTypes.LINK_TEXT) != null) {
                            visitor.consumeHtml(linkText)
                            return
                        }

                        // according to GFM_AUTOLINK rule in lexer, link either starts with scheme or with 'www.'
                        val linkDestination = if (hasSchema(linkText)) linkText else "http://$linkText"

                        val link = EntityConverter.replaceEntities(linkText, true, false)
                        visitor.consumeTagOpen(node, "a", "href=\"${LinkMap.normalizeDestination(linkDestination, false)}\"")
                        visitor.consumeHtml(link)
                        visitor.consumeTagClose("a")
                    }

                    private fun hasSchema(linkText: CharSequence): Boolean {
                        val index = linkText.indexOf('/')
                        if (index == -1) return false
                        return index != 0
                                && index + 1 < linkText.length
                                && linkText[index - 1] == ':'
                                && linkText[index + 1] == '/'
                    }
                },

                MarkdownElementTypes.LIST_ITEM to CheckedListItemGeneratingProvider()
        )
    }
}