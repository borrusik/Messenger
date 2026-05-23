package protocol;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

public class XmlProtocol {

    public static String toXml(Message message) {
        return "<message>"
                + "<type>" + escape(message.getType().name()) + "</type>"
                + "<from>" + escape(message.getFrom()) + "</from>"
                + "<text>" + escape(message.getText()) + "</text>"
                + "</message>";
    }

    public static Message fromXml(String xml) {
        try {
            Document document = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            Element root = document.getDocumentElement();

            MessageType type = MessageType.valueOf(getTagValue(root, "type"));
            String from = getTagValue(root, "from");
            String text = getTagValue(root, "text");

            return new Message(type, from, text);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML message: " + xml, e);
        }
    }

    private static String getTagValue(Element root, String tagName) {
        return root.getElementsByTagName(tagName)
                .item(0)
                .getTextContent();
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