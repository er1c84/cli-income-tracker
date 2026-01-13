import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class TipCalculatorFx extends Application {

    private double dragOffsetX;
    private double dragOffsetY;

    @Override
    public void start(Stage stage) {
        //Header
        Label title = new Label("Income Tracker");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        Button closeBtn = new Button("✕");
        closeBtn.setFocusTraversable(false);
        closeBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-font-size: 14px;
            -fx-padding: 2 8 2 8;
        """);
        closeBtn.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox header = new HBox(8, title, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        //Body (placeholder for future UI)
        Label body = new Label("Hello World (UI shell)");
        body.setStyle("-fx-opacity: 0.75;");

        VBox root = new VBox(12, header, body);
        root.setPadding(new Insets(14));
        root.setStyle("""
            -fx-background-color: #FFF6B3;
            -fx-background-radius: 16;
            -fx-border-radius: 32;
            -fx-border-color: rgba(0,0,0,0.12);
            -fx-border-width: 1;
        """);

        Scene scene = new Scene(root, 280, 180);

        //Widget behavior
        stage.initStyle(StageStyle.UNDECORATED); // removes title bar
        stage.setAlwaysOnTop(true);              // stays above other windows
        stage.setScene(scene);

        //Drag window by grabbing anywhere on the root
        root.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        root.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
