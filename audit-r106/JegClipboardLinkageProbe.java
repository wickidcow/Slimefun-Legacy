import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

/** Execute the actual packaged JEG methods with independently resolved floor API dependencies. */
public final class JegClipboardLinkageProbe {
    private JegClipboardLinkageProbe() {}

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.balugaq.jeg.utils.ClipboardUtil");
        for (String value : List.of("slimefun:OLD_ITEM_ID", "", "owner's text & literal")) {
            Component label = Component.text("Existing item");
            Component hover = Component.text("Existing owner lore");
            Object simple = type.getMethod("makeComponentPaper", Component.class, String.class)
                    .invoke(null, label, value);
            Object detailed = type.getMethod("makeComponentPaper", Component.class, Component.class, String.class)
                    .invoke(null, label, hover, value);
            for (Object result : List.of(simple, detailed)) {
                if (!(result instanceof Component component)
                        || component.clickEvent() == null
                        || component.clickEvent().action() != ClickEvent.Action.COPY_TO_CLIPBOARD
                        || !value.equals(component.clickEvent().value())
                        || component.hoverEvent() == null) {
                    throw new AssertionError("Clipboard payload, hover, or component was not retained");
                }
            }
        }
        System.out.println("JEG_CLIPBOARD_FLOOR_PASS methods=2 scenarios=6");
    }
}
