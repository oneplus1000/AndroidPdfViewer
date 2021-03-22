package com.github.barteksc.pdfviewer.model;


import java.util.List;

public class DualPageDisplay {
    int pageLeft = -1;
    int pageRight = -1;

    public DualPageDisplay(int pageLeft, int pageRight) {
        this.pageLeft = pageLeft;
        this.pageRight = pageRight;
    }

    public int getPageLeft() {
        return this.pageLeft;
    }

    public int getPageRight() {
        return this.pageRight;
    }

    //หา index ของ array โดยหาจาก pageLeftOrRight ซึ่งอาจจะเป็น pageLeft หรือ pageRight ก็ได้
    static public int findIndexByPage(List<DualPageDisplay> dualPageDisplays, int pageLeftOrRight) {
        int i = 0;
        for (DualPageDisplay d : dualPageDisplays) {
            if (d.pageLeft == pageLeftOrRight || d.pageRight == pageLeftOrRight) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public String debug() {
        return " pageLeft:" + pageLeft + " pageRight:" + pageRight;
    }

}