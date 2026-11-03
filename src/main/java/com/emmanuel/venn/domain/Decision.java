package com.emmanuel.venn.domain;

/** Accepted when there is no rejection reason. */
public record Decision(RejectionReason rejectionReason) {

    public static Decision accepted() {
        return new Decision(null);
    }

    public static Decision rejected(RejectionReason reason) {
        return new Decision(reason);
    }

    public boolean isAccepted() {
        return rejectionReason == null;
    }
}
