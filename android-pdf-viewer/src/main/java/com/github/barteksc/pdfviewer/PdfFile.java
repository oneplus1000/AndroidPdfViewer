/**
 * Copyright 2017 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.github.barteksc.pdfviewer.exception.PageRenderingException;
import com.github.barteksc.pdfviewer.model.DualPageDisplay;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.github.barteksc.pdfviewer.util.PageSizeCalculator;
import com.github.barteksc.pdfviewer.util.SnapEdge;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;
import com.shockwave.pdfium.util.SizeF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PdfFile {

    private static final Object lock = new Object();
    private PdfDocument pdfDocument;
    private PdfiumCore pdfiumCore;
    private int pagesCount = 0;
    /**
     * Original page sizes
     */
    private List<Size> originalPageSizes = new ArrayList<>();
    /**
     * Scaled page sizes
     */
    private List<SizeF> pageSizes = new ArrayList<>();
    /**
     * Opened pages with indicator whether opening was successful
     */
    private SparseBooleanArray openedPages = new SparseBooleanArray();
    /**
     * Page with maximum width
     */
    private Size originalMaxWidthPageSize = new Size(0, 0);
    /**
     * Page with maximum height
     */
    private Size originalMaxHeightPageSize = new Size(0, 0);
    /**
     * Scaled page with maximum height
     */
    private SizeF maxHeightPageSize = new SizeF(0, 0);
    /**
     * Scaled page with maximum width
     */
    private SizeF maxWidthPageSize = new SizeF(0, 0);
    /**
     * True if scrolling is vertical, else it's horizontal
     */
    private boolean isVertical;
    /**
     * Fixed spacing between pages in pixels
     */
    private int spacingPx;
    /**
     * Calculate spacing automatically so each page fits on it's own in the center of the view
     */
    private boolean autoSpacing;
    /**
     * Calculated offsets for pages
     */
    private List<Float> pageOffsets = new ArrayList<>();
    /**
     * Calculated auto spacing for pages
     */
    private List<Float> pageSpacing = new ArrayList<>();
    /**
     * Calculated document length (width or height, depending on swipe mode)
     */
    private float documentLength = 0;
    private final FitPolicy pageFitPolicy;
    /**
     * True if every page should fit separately according to the FitPolicy,
     * else the largest page fits and other pages scale relatively
     */
    private final boolean fitEachPage;
    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    private int[] originalUserPages;


    //request อยากให้แสดงแบบไหน
    private int requestDisplayDualPageType = PDFView.Configurator.REQUEST_DISPLAY_DUALPAGE_TYPE_ONLY_SINGLE_PAGE;
    //แสดงผลจริง
    private int realDisplayDualPageType = PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SINGLE_PAGE;

    private Size viewSize = null;

    private boolean isRTL = false;

    private ArrayList<Integer> pageBreaks;

    PdfFile(PdfiumCore pdfiumCore, PdfDocument pdfDocument, FitPolicy pageFitPolicy, Size viewSize, boolean isRTL, int[] originalUserPages,
            boolean isVertical, int spacing, boolean autoSpacing, boolean fitEachPage,
            int requestDisplayDualPageType, ArrayList<Integer> pageBreaks) {
        this.pdfiumCore = pdfiumCore;
        this.pdfDocument = pdfDocument;
        this.pageFitPolicy = pageFitPolicy;
        this.originalUserPages = originalUserPages;
        this.isVertical = isVertical;
        this.spacingPx = spacing;
        this.autoSpacing = autoSpacing;
        this.fitEachPage = fitEachPage;
        this.requestDisplayDualPageType = requestDisplayDualPageType;
        this.isRTL = isRTL;
        this.pageBreaks = pageBreaks;
        setup(viewSize);
    }

    public int getRequestDisplayDualPageType() {
        return this.requestDisplayDualPageType;
    }

    public int getRealDisplayDualPageType() {
        return this.realDisplayDualPageType;
    }

    private void setup(Size viewSize) {

        if (originalUserPages != null) {
            pagesCount = originalUserPages.length;
        } else {
            pagesCount = pdfiumCore.getPageCount(pdfDocument);
        }

        for (int i = 0; i < pagesCount; i++) {
            Size pageSize = pdfiumCore.getPageSize(pdfDocument, documentPage(i));
            if (pageSize.getWidth() > originalMaxWidthPageSize.getWidth()) {
                originalMaxWidthPageSize = pageSize;
            }
            if (pageSize.getHeight() > originalMaxHeightPageSize.getHeight()) {
                originalMaxHeightPageSize = pageSize;
            }
            originalPageSizes.add(pageSize);
        }

        recalculatePageSizes(viewSize);
    }

    /**
     * Call after view size change to recalculate page sizes, offsets and document length
     *
     * @param viewSize new size of changed view
     */
    public void recalculatePageSizes(Size viewSize) {
        this.viewSize = viewSize;
        pageSizes.clear();
        PageSizeCalculator calculator = new PageSizeCalculator(pageFitPolicy, originalMaxWidthPageSize,
                originalMaxHeightPageSize, viewSize, fitEachPage);
        maxWidthPageSize = calculator.getOptimalMaxWidthPageSize();
        maxHeightPageSize = calculator.getOptimalMaxHeightPageSize();

        int viewSizeHalfWidth = viewSize.getWidth() / 2;
        int realDisplayDualPage = PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SINGLE_PAGE;

        boolean maybeCanDisplayDualPage = this.autoSpacing &&
                !this.isVertical &&
                this.requestDisplayDualPageType == PDFView.Configurator.REQUEST_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE_IF_IT_CAN;
        if (maybeCanDisplayDualPage) {
            realDisplayDualPage = PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE;
        }
        for (Size size : originalPageSizes) {
            SizeF pageSize = calculator.calculate(size);
            pageSizes.add(pageSize);
            //ตรวจสอบว่าหน้าจอพอดีกับการแสดงหน้าคู่หรือไม่??
            if (maybeCanDisplayDualPage) {
                if (pageSize.getWidth() > viewSizeHalfWidth) { //ขนาดเกินแสดงไม่ได้
                    realDisplayDualPage = PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SINGLE_PAGE;
                }
            }
        }

        this.realDisplayDualPageType = realDisplayDualPage;

        if (this.realDisplayDualPageType == PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE) {
            //คำนวนหน้าที่จะต้องติดกัน
            this.calcDualPages();
        }
        if (autoSpacing) {
            prepareAutoSpacing(viewSize);
        }
        prepareDocLen();
        preparePagesOffset();
    }

    final private List<DualPageDisplay> dualPageDisplays = new ArrayList<>();

    public List<DualPageDisplay> getDualPageDisplays() {
        return this.dualPageDisplays;
    }

    //คำนวนหน้าที่จะต้องติดกัน
    private void calcDualPages() {
        this.dualPageDisplays.clear(); //ลบของเก่าออกให้หมด
        int pageCount = this.getPagesCount();
        List<Integer> breaks = this.pageBreaks;
        if (isRTL) {
            int i = pageCount - 1;
            while (true) {
                if (i < 0) {
                    break;
                }
                int pageRight = i;
                if (breaks.contains(pageCount - i)) {
                    dualPageDisplays.add(new DualPageDisplay(-1, pageRight));
                    i--;
                    continue;
                }
                i--;
                if (i < 0) {
                    dualPageDisplays.add(new DualPageDisplay(-1, pageRight));
                    break;
                }
                int pageLeft = i;
                dualPageDisplays.add(new DualPageDisplay(pageLeft, pageRight));
                i--;
            }
            Collections.reverse(dualPageDisplays);
        } else {
            int i = 0;
            while (true) {
                if (i >= pageCount) {
                    break;
                }
                int pageLeft = i;

                if (breaks.contains(i + 1)) {
                    dualPageDisplays.add(new DualPageDisplay(pageLeft, -1));
                    i++;
                    continue;
                }

                i++;
                if (i >= pageCount) {
                    dualPageDisplays.add(new DualPageDisplay(pageLeft, -1));
                    break;
                }
                int pageRight = i;
                dualPageDisplays.add(new DualPageDisplay(pageLeft, pageRight));
                i++;
            }
        }


        for (DualPageDisplay d : dualPageDisplays) {
            Log.d("XX", "KK " + d.debug());
        }
    }

    public int getPagesCount() {
        return pagesCount;
    }

    public int getPageCountForDualPage() {
        return this.dualPageDisplays.size();
    }

    public SizeF getPageSize(int pageIndex) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return new SizeF(0, 0);
        }
        return pageSizes.get(pageIndex);
    }

    public SizeF getScaledPageSize(int pageIndex, float zoom) {
        SizeF size = getPageSize(pageIndex);
        return new SizeF(size.getWidth() * zoom, size.getHeight() * zoom);
    }

    /**
     * get page size with biggest dimension (width in vertical mode and height in horizontal mode)
     *
     * @return size of page
     */
    public SizeF getMaxPageSize() {
        return isVertical ? maxWidthPageSize : maxHeightPageSize;
        //return isVertical ? maxWidthPageSize : new SizeF(maxHeightPageSize.getWidth(),1553f);
    }

    public float getMaxPageWidth() {
        return getMaxPageSize().getWidth();
    }

    public float getMaxPageHeight(int index) {
        //return 1553f;
        //Log.d("YYY","  index: "+index + "  getHeight: " + getPageSize(index).getHeight());
        //return getPageSize(index).getHeight();
        //return getMaxPageSize().getHeight();
        if (getPageSize(index).getHeight() > getMaxPageSize().getHeight()) {
            return getPageSize(index).getHeight();
        }
        return getMaxPageSize().getHeight();
    }

    private void prepareAutoSpacing(Size viewSize) {
        pageSpacing.clear();
        int pagesCount = this.getPagesCount();
        for (int i = 0; i < pagesCount; i++) {
            SizeF pageSize = pageSizes.get(i);
            float spacing = Math.max(0, isVertical ? viewSize.getHeight() - pageSize.getHeight() :
                    viewSize.getWidth() - pageSize.getWidth());
            //สำหรับหน้าคู่
            //if (this.realDisplayDualPageType == PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE) {
            //    spacing = Math.max(0, isVertical ? (viewSize.getHeight() / 2f) - pageSize.getHeight() :
            //            (viewSize.getWidth() / 2f) - pageSize.getWidth());
            //}
            if (i < pagesCount - 1) {
                spacing += spacingPx;
            }
            pageSpacing.add(spacing);
        }
    }

    private void prepareDocLen() {

        //หน้าคู่
        //if (this.viewSize != null && this.realDisplayDualPageType == PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE) {
        //    int count = this.dualPageDisplays.size();
        //    this.documentLength = count * this.viewSize.getWidth();
        //    return;
        //}

        float length = 0;
        for (int i = 0; i < getPagesCount(); i++) {
            SizeF pageSize = pageSizes.get(i);
            length += isVertical ? pageSize.getHeight() : pageSize.getWidth();
            if (autoSpacing) {
                length += pageSpacing.get(i);
            } else if (i < getPagesCount() - 1) {
                length += spacingPx;
            }
        }
        documentLength = length;
    }

    private void preparePagesOffset() {
        pageOffsets.clear();
        float offset = 0;
        for (int i = 0; i < getPagesCount(); i++) {
            SizeF pageSize = pageSizes.get(i);
            float size = isVertical ? pageSize.getHeight() : pageSize.getWidth();
            if (autoSpacing) {
                offset += pageSpacing.get(i) / 2f;
                if (i == 0) {
                    offset -= spacingPx / 2f;
                } else if (i == getPagesCount() - 1) {
                    offset += spacingPx / 2f;
                }
                pageOffsets.add(offset);
                offset += size + pageSpacing.get(i) / 2f;
            } else {
                pageOffsets.add(offset);
                offset += size + spacingPx;
            }
        }
    }

    public float getDocLen(float zoom) {

        //หน้าคู่
        if (this.viewSize != null && this.realDisplayDualPageType == PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE) {
            int count = this.dualPageDisplays.size();
            float length = count * this.viewSize.getWidth();
            return length * zoom;
        }

        return documentLength * zoom;
    }

    /**
     * Get the page's height if swiping vertical, or width if swiping horizontal.
     */
    public float getPageLength(int pageIndex, float zoom) {
        SizeF size = getPageSize(pageIndex);
        return (isVertical ? size.getHeight() : size.getWidth()) * zoom;
    }

    public float getPageSpacing(int pageIndex, float zoom) {
        float spacing = autoSpacing ? pageSpacing.get(pageIndex) : spacingPx;
        return spacing * zoom;
    }

    public float getPageOffsetForLocalTranslationX(int pageIndex, float zoom) {

        //offset สำหรับหน้าคู่
        if (this.viewSize != null && this.realDisplayDualPageType == PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE) {
            int index = DualPageDisplay.findIndexByPage(this.dualPageDisplays, pageIndex);
            if (index != -1) {
                DualPageDisplay display = this.dualPageDisplays.get(index);

                //float offsetBefore = 0f;
                float offsetBefore = this.viewSize.getWidth() * (index);
                if (display.getPageLeft() == pageIndex) {
                    float offset = this.pageSizes.get(pageIndex).getWidth();
                    if (display.getPageRight() == -1) {  //ถ้าไม่มีหน้าคู่มันจะต้องอยู่ตรงกลาง
                        return (offsetBefore + (this.viewSize.getWidth() / 2f) - (offset / 2)) * zoom;
                    }
                    return (offsetBefore + (this.viewSize.getWidth() / 2f) - offset) * zoom;
                } else if (display.getPageRight() == pageIndex) {
                    if (display.getPageLeft() == -1) {//ถ้าไม่มีหน้าคู่มันจะต้องอยู่ตรงกลาง
                        float offset = this.pageSizes.get(pageIndex).getWidth();
                        return (offsetBefore + (this.viewSize.getWidth() / 2f) - (offset / 2)) * zoom;
                    }
                    return (offsetBefore + (this.viewSize.getWidth() / 2f)) * zoom;
                }

            }
        }

        return getPageOffset(pageIndex, zoom);
    }

    public Float snapOffsetForPage(int pageIndex, SnapEdge edge) {

        if (this.viewSize == null) {
            return null;
        }

        if (this.realDisplayDualPageType != PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE) {
            return null;
        }

        int index = DualPageDisplay.findIndexByPage(this.dualPageDisplays, pageIndex);
        if (index == -1) {
            return null;
        }

        return (float) this.viewSize.getWidth() * index;
    }

    /**
     * Get primary page offset, that is Y for vertical scroll and X for horizontal scroll
     */
    public float getPageOffset(int pageIndex, float zoom) {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return 0;
        }
        return pageOffsets.get(pageIndex) * zoom;
    }

    /**
     * Get secondary page offset, that is X for vertical scroll and Y for horizontal scroll
     */
    public float getSecondaryPageOffset(int pageIndex, float zoom) {
        SizeF pageSize = getPageSize(pageIndex);
        if (isVertical) {
            float maxWidth = getMaxPageWidth();
            return zoom * (maxWidth - pageSize.getWidth()) / 2; //x
        } else {

            float maxHeight = getMaxPageHeight(pageIndex);
            //Log.d("XXX","zoom"+zoom+" * (maxHeight:"+maxHeight+" - pageSize.getHeight() "+pageSize.getHeight()+") / 2");
            return zoom * (maxHeight - pageSize.getHeight()) / 2; //y
        }
    }

    //return int[2]  โดย {firstPage,lastPage,}
    public int[] getPageAtOffsetForDualPage(float offsetFirst, float zoom) {

        int count = this.dualPageDisplays.size();
        float viewWidth = this.viewSize.getWidth() * zoom;
        int selected = -1;
        for (int i = 0; i < count; i++) {
            float offset = viewWidth * i;
            if (offset >= offsetFirst) {
                selected = i;
                break;
            }
        }

        if (selected < 0) {
            selected = 0;
        }


        DualPageDisplay displayStart = null;
        DualPageDisplay displayEnd = null;
        if (selected - 1 >= 0) {
            int prev = selected - 1;
            displayStart = this.dualPageDisplays.get(prev);
        } else {
            displayStart = this.dualPageDisplays.get(selected);
        }

        if (selected + 1 < count) {
            int next = selected + 1;
            displayEnd = this.dualPageDisplays.get(next);
        } else {
            displayEnd = this.dualPageDisplays.get(selected);
        }

        int firstPage = displayStart.getPageLeft();
        int lastPage = firstPage;
        if (firstPage == -1) {
            firstPage = displayStart.getPageRight();
        }
        if (displayStart.getPageRight() != -1) {
            lastPage = displayStart.getPageRight();
        }

        if (displayEnd != null) {
            lastPage = displayEnd.getPageRight();
            if (lastPage < displayEnd.getPageLeft()) {
                lastPage = displayEnd.getPageLeft();
            }
        }
        
        return new int[]{firstPage, lastPage};
    }

    public int getPageAtOffset(float offset, float zoom) {
        /*
        if (this.viewSize != null && this.realDisplayDualPageType == PDFView.Configurator.REAL_DISPLAY_DUALPAGE_TYPE_SHOW_DUAL_PAGE) {
            int dualPageCount = this.dualPageDisplays.size();
            int index = 0;
            for (int i = 0; i < dualPageCount; i++) {
                float off = this.viewSize.getWidth() * i;
                if (off >= offset) {
                    break;
                }
                index++;
            }
            index = index - 1;
            if(index < 0 ){
                index = 0;
            }
            DualPageDisplay display = this.dualPageDisplays.get(index);
            if (display.getPageLeft() != -1) {
                return display.getPageLeft();
            } else if (display.getPageRight() != -1) {
                return display.getPageRight();
            }

        }*/

        int currentPage = 0;
        for (int i = 0; i < getPagesCount(); i++) {
            float off = pageOffsets.get(i) * zoom - getPageSpacing(i, zoom) / 2f;
            if (off >= offset) {
                break;
            }
            currentPage++;
        }
        return --currentPage >= 0 ? currentPage : 0;
    }

    public boolean openPage(int pageIndex) throws PageRenderingException {
        int docPage = documentPage(pageIndex);
        if (docPage < 0) {
            return false;
        }

        synchronized (lock) {
            if (openedPages.indexOfKey(docPage) < 0) {
                try {
                    pdfiumCore.openPage(pdfDocument, docPage);
                    openedPages.put(docPage, true);
                    return true;
                } catch (Exception e) {
                    openedPages.put(docPage, false);
                    throw new PageRenderingException(pageIndex, e);
                }
            }
            return false;
        }
    }

    public boolean pageHasError(int pageIndex) {
        int docPage = documentPage(pageIndex);
        return !openedPages.get(docPage, false);
    }

    public void renderPageBitmap(Bitmap bitmap, int pageIndex, Rect bounds, boolean annotationRendering) {
        int docPage = documentPage(pageIndex);
        pdfiumCore.renderPageBitmap(pdfDocument, bitmap, docPage,
                bounds.left, bounds.top, bounds.width(), bounds.height(), annotationRendering);
    }

    public PdfDocument.Meta getMetaData() {
        if (pdfDocument == null) {
            return null;
        }
        return pdfiumCore.getDocumentMeta(pdfDocument);
    }

    public List<PdfDocument.Bookmark> getBookmarks() {
        if (pdfDocument == null) {
            return new ArrayList<>();
        }
        return pdfiumCore.getTableOfContents(pdfDocument);
    }

    public List<PdfDocument.Link> getPageLinks(int pageIndex) {
        int docPage = documentPage(pageIndex);
        return pdfiumCore.getPageLinks(pdfDocument, docPage);
    }

    public RectF mapRectToDevice(int pageIndex, int startX, int startY, int sizeX, int sizeY,
                                 RectF rect) {
        int docPage = documentPage(pageIndex);
        return pdfiumCore.mapRectToDevice(pdfDocument, docPage, startX, startY, sizeX, sizeY, 0, rect);
    }

    public void dispose() {
        if (pdfiumCore != null && pdfDocument != null) {
            pdfiumCore.closeDocument(pdfDocument);
        }

        pdfDocument = null;
        originalUserPages = null;
    }

    private void printDebug(String title) {
        StackTraceElement[] eles = Thread.currentThread().getStackTrace();
        if (eles == null) {
            return;
        }
        Log.d("printDebug", "-----------" + title + "------------");
        for (StackTraceElement ele : eles) {
            Log.d("printDebug", ele.getMethodName());
        }
    }

    /**
     * Given the UserPage number, this method restrict it
     * to be sure it's an existing page. It takes care of
     * using the user defined pages if any.
     *
     * @param userPage A page number.
     * @return A restricted valid page number (example : -2 => 0)
     */
    public int determineValidPageNumberFrom(int userPage) {
        if (userPage <= 0) {
            return 0;
        }
        if (originalUserPages != null) {
            if (userPage >= originalUserPages.length) {
                return originalUserPages.length - 1;
            }
        } else {
            if (userPage >= getPagesCount()) {
                return getPagesCount() - 1;
            }
        }
        return userPage;
    }

    public int documentPage(int userPage) {
        int documentPage = userPage;
        if (originalUserPages != null) {
            if (userPage < 0 || userPage >= originalUserPages.length) {
                return -1;
            } else {
                documentPage = originalUserPages[userPage];
            }
        }

        if (documentPage < 0 || userPage >= getPagesCount()) {
            return -1;
        }

        return documentPage;
    }


}
