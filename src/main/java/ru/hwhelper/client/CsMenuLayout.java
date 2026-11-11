package ru.hwhelper.client;

/** Layout in GUI pixels: text remains at its native font size on small windows. */
record CsMenuLayout(int x,int y,int w,int h,int header,int bodyTop,int bodyBottom,int columns,int cardWidth) {
    static CsMenuLayout create(int width,int height,boolean compactColumns) {
        return create(width,height,compactColumns,Integer.MAX_VALUE);
    }
    static CsMenuLayout create(int width,int height,boolean compactColumns,int moduleCount) {
        int w=Math.min(680,width-24),h=Math.min(410,height-24);
        int x=(width-w)/2,y=(height-h)/2;
        int header=w<430?68:52;
        int columns=Math.min(Math.max(1,moduleCount),w>=580&&!compactColumns?3:w>=400?2:1);
        int cardWidth=(w-24-(columns-1)*8)/columns;
        return new CsMenuLayout(x,y,w,h,header,y+header+52,y+h-26,columns,cardWidth);
    }
}
