package com.abbos.precisiontrigger.app;

import com.abbos.precisiontrigger.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class PrecisionTriggerApplication extends Application {
    private PrecisionTriggerFacade facade;

    @Override
    public void start(Stage stage) throws Exception {
        ApplicationRuntime runtime = new ApplicationBootstrap().bootstrap();
        facade = new PrecisionTriggerFacade(runtime);
        FXMLLoader loader = new FXMLLoader(PrecisionTriggerApplication.class.getResource("/ui/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1100, 760);
        MainController controller = loader.getController();
        controller.setFacade(facade);
        stage.setTitle("precision-trigger");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (facade != null) {
            facade.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}