package me.iyh.gui;

import javax.swing.*;

public class Frame extends JFrame {

    public Frame(String title, int width, int height) {

        // set title for the app
        this.setTitle(title);

        // set size for the app
        this.setSize(width, height);

        // set close button
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);

        // prevent user resize the app
        this.setResizable(false);

    }

}
