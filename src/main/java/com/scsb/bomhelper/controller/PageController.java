package com.scsb.bomhelper.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    /**
     * 💡 這是「大框架」：包含側邊欄與導覽列
     */
    @GetMapping("/")
    public String dashboardShell() {
        return "index"; // 指向 templates/index.html
    }

    /**
     * 💡 以下是給 Iframe 讀取的「內容頁」
     */
    @GetMapping("/search")
    public String searchPage() {
        return "search"; // 指向 templates/search.html
    }

    @GetMapping("/upload")
    public String uploadPage() {
        return "upload"; // 指向 templates/upload.html
    }
}