module sk.vava.royalmate {
    // --- Required Modules ---

    // JavaFX modules needed for UI and FXML loading
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    // Standard Java module for logging
    requires java.logging;

    requires java.desktop;

    // Standard Java module for JDBC database access
    requires java.sql;
    requires mysql.connector.j;
    requires static lombok;

    // --- Package Exposure ---

    // Open the 'controller' package TO the 'javafx.fxml' module.
    // This allows FXMLLoader to instantiate controllers and access @FXML fields/methods via reflection.
    opens sk.vava.royalmate.controller to javafx.fxml;

    // Open the 'app' package TO 'javafx.graphics'.
    // This allows the JavaFX runtime to launch your Application class (RoyalMate).
    opens sk.vava.royalmate.app to javafx.graphics;

    // If your FXML files reference model classes directly (e.g., for data binding in TableView),
    // you'll need to open the 'model' package later.
    // opens sk.vava.royalmate.model to javafx.base; // Add this when you have models used in FX properties

    // --- Exported Packages ---

    // Export the 'app' package. This makes the RoyalMate class visible
    // so it can be launched, potentially by external tools or launchers.
    exports sk.vava.royalmate.app;

    // You generally DON'T need to export 'controller', 'service', 'data', 'util'
    // unless you intend for *other external modules* to directly use them,
    // which is unlikely for a self-contained application.

    // DO NOT export or open the base package 'sk.vava.royalmate' itself
    // unless you place classes directly within it (which is not standard practice).
}

