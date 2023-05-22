package com.hmdp.utils;

public interface ILock {
    /*
    * 获取锁
    * @Para timeoutSec 超时时间
    * */
    boolean tryLock(long timeoutSec);

    /*
    * 释放锁
    * */
    void unlock();
}
