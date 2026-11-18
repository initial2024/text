package com.omnigate.integrated;

/**
 * Defensive file-size metadata gate for Android SAF file selection.
 * A provider may omit OpenableColumns.SIZE; a regular file descriptor may
 * still report its size. Neither value attests uploaded bytes or upload success.
 */
final class SafFileSizeBounds {
    private SafFileSizeBounds() {}

    static long reportedSize(long openableSize,long descriptorSize) {
        if(openableSize<0)return descriptorSize>=0?descriptorSize:-1L;
        if(descriptorSize<0)return openableSize;
        // Conservative on conflicting metadata. No trust in a smaller value.
        return Math.max(openableSize,descriptorSize);
    }

    /** Fixed UI labels only. Do not reveal provider exception messages,
     * URI, filenames, account IDs or document contents. */
    static String safeFailureLabel(Throwable error) {
        if(error instanceof SecurityException)
            return "FILE_PERMISSION_DENIED：系统文件读取授权不足或已过期";
        if(error instanceof IllegalArgumentException){
            String code=error.getMessage();
            if("file_count".equals(code))
                return "FILE_COUNT_REJECTED：文件数量超限或网页不允许多选";
            if("invalid_document_uri".equals(code))
                return "FILE_URI_REJECTED：无效或重复的系统文件";
            if("unknown_or_oversize_file".equals(code))
                return "FILE_SIZE_REJECTED：文件大小无法确认或超过安全上限";
        }
        if(error instanceof java.io.IOException)
            return "FILE_READ_REJECTED：无法读取系统文档";
        return "FILE_PICKER_REJECTED：文件提供者发生未知异常";
    }

    static long checkedTotal(long current,long size,long maxItem,long maxTotal) {
        if(current<0||size<0||maxItem<0||maxTotal<0||
                size>maxItem||size>maxTotal||current>maxTotal-size)
            throw new IllegalArgumentException("unknown_or_oversize_file");
        return current+size;
    }
}
