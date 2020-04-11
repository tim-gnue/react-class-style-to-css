import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransformToCSS extends AnAction {
    @Override
    public void update(@NotNull final AnActionEvent e) {
        final Project project = e.getProject();
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        boolean isReactFile = false;
        if (virtualFile != null) {
            final String extension = virtualFile.getExtension();
            isReactFile = extension != null && (extension.equals("jsx") || extension.equals("tsx"));
        }
        e.getPresentation().setEnabledAndVisible(project != null && editor != null && isReactFile && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        String selectedText = editor.getSelectionModel().getSelectedText();
        assert selectedText != null;
        String transformedText = this.findObjectAndTransform(selectedText);
        // Copy to clipboard.
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(
                        new StringSelection(transformedText),
                        null
                );
        // Create a popup balloon.
        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder("Copied to Clipboard!", MessageType.INFO, null)
                .setFadeoutTime(7500)
                .createBalloon()
                .show(new RelativePoint(MouseInfo.getPointerInfo().getLocation()), Balloon.Position.atRight);
    }

    private String findObjectAndTransform(String text) {
        // Find object {...} and transform each block
        String regex = "\\{[^;]+\\}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        List<String> finalText = new ArrayList<String>();
        while (matcher.find()) {
            String tmp = this.transform(matcher.group());
            finalText.add(tmp);
        }
        return String.join("\n", finalText);
    }

    private String transform(String text) {
        String finalText = text;
        // Transform number to number + px
        // Matched case must end with a "}" or ","
        // Possible cases: ": 20}", ":20}', ":20 }", ": 20 }", ":20,", ": 20," ...
        // Prevents matching cases like "rgba(0, 0, 0, 0.1)" or "#222" ...
        String regex = ":\\s*(\\d+)(\\s*[\\,\\}])";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(finalText);
        finalText = matcher.replaceAll(x -> ": " + x.group(1) + "px" + x.group(2));
        // Remove the double quote.
        // Matched case must end with a "}" or ","
        // Invalid Case: "url(require("./asset/damnBackground.png"))"
        // Invalid Case 2: {margin: "20px", padding: "40px"}. It will not match the whole line due to the check of ([^:]+)
        String regex2 = "\"([^:]+)\"\\s*([\\,\\}])";
        Pattern pattern2 = Pattern.compile(regex2);
        Matcher matcher2 = pattern2.matcher(finalText);
        finalText = matcher2.replaceAll(x -> x.group(1) + x.group(2));
        // Transform from xxxYYY to xxx-yyy.
        // Matched case must end with a ":" character.
        // Possible case: "marginBottom:"
        // Invalid Case: `url(require("./asset/damnBackground.png"))`
        String regex3 = "([A-Z])(\\w+:)";
        Pattern pattern3 = Pattern.compile(regex3);
        Matcher matcher3 = pattern3.matcher(finalText);
        finalText = matcher3.replaceAll(x -> "-" + x.group(1).toLowerCase() + x.group(2));
        // Transform `url(${require("./asset/damnImage.png")})`
        // to url("./asset/damnImage.png")
        String regex4 = "`url\\(\\$\\{require\\((.*)\\)\\}\\)`";
        Pattern pattern4 = Pattern.compile(regex4);
        Matcher matcher4 = pattern4.matcher(finalText);
        finalText = matcher4.replaceAll(x -> "url(" + x.group(1) + ")");
        // Transform comma to semicolon
        String regex5 = "(:[^:]+),";
        Pattern pattern5 = Pattern.compile(regex5);
        Matcher matcher5 = pattern5.matcher(finalText);
        finalText = matcher5.replaceAll(x -> x.group(1) + ";");
        return finalText;
    }
}
