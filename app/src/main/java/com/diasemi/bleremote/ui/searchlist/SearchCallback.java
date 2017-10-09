package com.diasemi.bleremote.ui.searchlist;

import java.util.ArrayList;

public abstract interface SearchCallback {

    public abstract void onSearchCompleted(boolean success, ArrayList<SearchItem> searchItems,
            ArrayList<String> errorList);
}
