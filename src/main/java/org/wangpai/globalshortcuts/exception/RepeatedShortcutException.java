package org.wangpai.globalshortcuts.exception;

/**
 * 在不允许设置重复快捷键时，设置了重复快捷键时引发的异常
 *
 * @since 2022-9-30
 */
public class RepeatedShortcutException extends GlobalShortcutsException {
    public RepeatedShortcutException() {
        super("异常：此快捷键已存在");
    }

    public RepeatedShortcutException(String msg) {
        super(msg);
    }

    public RepeatedShortcutException(String msg, Object obj) {
        super(msg, obj);
    }
}
