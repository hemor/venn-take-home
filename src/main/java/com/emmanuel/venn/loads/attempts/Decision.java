package com.emmanuel.venn.loads.attempts;

/** Accepted when there is no rejection reason. The reason is the name of the rule that was broken. */
public record Decision(String rejectionReason) {

    public static Decision accepted() {
        return new Decision(null);
    }

    public static Decision rejected(String reason) {
        return new Decision(reason);
    }

    public boolean isAccepted() {
        return rejectionReason == null;
    }
}
