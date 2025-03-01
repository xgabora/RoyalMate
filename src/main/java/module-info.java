module sk.vava.royalmate {
    requires javafx.controls;
    requires javafx.fxml;


    opens sk.vava.royalmate to javafx.fxml;
    exports sk.vava.royalmate;
}