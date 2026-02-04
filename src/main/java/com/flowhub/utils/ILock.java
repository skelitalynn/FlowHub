package com.flowhub.utils;

public interface ILock {
    boolean tryLock(long timeoutSec);
    void unLock();
}
