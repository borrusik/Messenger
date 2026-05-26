package protocol;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

public class XmlProtocol {

    public static String toXml(Message message) {
        return "<message>"
                + "<type>" + escape(message.getType().name()) + "</type>"
                + "<from>" + escape(message.getFrom()) + "</from>"
                + "<to>" + escape(message.getTo()) + "</to>"
                + "<id>" + escape(message.getId()) + "</id>"
                + "<fileName>" + escape(message.getFileName()) + "</fileName>"
                + "<mimeType>" + escape(message.getMimeType()) + "</mimeType>"
                + "<text>" + escape(message.getText()) + "</text>"
                + "</message>";
    }

    public static Message fromXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("XML message is empty");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            Document document = factory
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            Element root = document.getDocumentElement();

            if (!"message".equals(root.getTagName())) {
                throw new IllegalArgumentException("Root tag must be message");
            }

            MessageType type = MessageType.valueOf(getTagValue(root, "type"));
            String from = getTagValue(root, "from");
            String to = getTagValueSafe(root, "to");
            String id = getTagValueSafe(root, "id");
            String fileName = getTagValueSafe(root, "fileName");
            String mimeType = getTagValueSafe(root, "mimeType");
            String text = getTagValue(root, "text");

            return new Message(type, from, to, text, id, fileName, mimeType);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML message: " + xml, e);
        }
    }

    private static String getTagValue(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException("Tag is missing: " + tagName);
        }

        return nodes.item(0).getTextContent();
    }
    
    private static String getTagValueSafe(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
