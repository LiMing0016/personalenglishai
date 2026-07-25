package com.personalenglishai.backend.service.vocabulary;

import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

final class VocabularyMarkdownValidator {

    private static final Parser PARSER = Parser.builder().build();

    private VocabularyMarkdownValidator() {
    }

    static boolean containsRawHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return false;
        }
        return containsRawHtml(PARSER.parse(markdown));
    }

    private static boolean containsRawHtml(Node node) {
        for (Node current = node; current != null; current = current.getNext()) {
            if (current instanceof HtmlBlock || current instanceof HtmlInline
                    || containsRawHtml(current.getFirstChild())) {
                return true;
            }
        }
        return false;
    }
}
