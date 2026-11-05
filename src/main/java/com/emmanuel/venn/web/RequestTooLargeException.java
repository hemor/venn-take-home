package com.emmanuel.venn.web;

import java.io.IOException;

public class RequestTooLargeException extends IOException {

    RequestTooLargeException(int maxBytes) {
        super("Request body exceeds " + maxBytes + " bytes");
    }
}
