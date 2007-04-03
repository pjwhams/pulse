package com.zutubi.pulse.scm;

/**
 * SCM capabilities are used to indicate what operations an SCM
 * implementation supports.
 */
public enum SCMCapability
{
    BROWSE,
    CHECKOUT_FILE,
    CHECKOUT_AT_REVISION,
    LATEST_REVISION,
    LIST_CHANGES,
    POLL,
    TAG,
    TEST_CONNECTION,
    UPDATE
}
