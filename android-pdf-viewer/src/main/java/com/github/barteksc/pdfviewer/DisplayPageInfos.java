package com.github.barteksc.pdfviewer;

import com.shockwave.pdfium.util.SizeF;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DisplayPageInfos {

    public List<PageFile> items = new ArrayList<>();

    public void clear(){
        this.items.clear();
    }

    public  int itemsSize(){
        return this.items.size();
    }

    public void addItem(List<Integer> pages ){
        PageFile p = new PageFile();
        p.pages = pages;
        this.items.add(p);
    }

    public PageFile getByPage(int page){
        for(PageFile item : this.items){
            if(item.pages.contains(page)){
                return item;
            }
        }
        return null;
    }



    public class PageFile {
        List<Integer> pages = new ArrayList<>();
        Map<Integer,SizeF> pageSizes = new HashMap<>();
        SizeF pageSize;
        Float pageOffset;
        Float pageSpacing;
    }
}
