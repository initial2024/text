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

    static long checkedTotal(long current,long size,long maxItem,long maxTotal) {
        if(current<0||size<0||maxItem<0||maxTotal<0||
                size>maxItem||size>maxTotal||current>maxTotal-size)
            throw new IllegalArgumentException("unknown_or_oversize_file");
        return current+size;
    }
}
