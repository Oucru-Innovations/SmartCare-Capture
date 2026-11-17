package com.smartcare.oximetry.sample;

import static com.smartcare.oximetry.sample.OximeterDataFileIndex.Bucket.PENDING;
import static com.smartcare.oximetry.sample.OximeterDataFileIndex.Bucket.TRASH;
import static com.smartcare.oximetry.sample.OximeterDataFileIndex.Bucket.UPLOADED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class FileRetentionTest {
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @Test
    public void bucketOf_splitsPendingUploadedTrash() {
        // MediaStore RELATIVE_PATH, SAF document id, absolute disk path
        assertEquals(PENDING, OximeterDataFileIndex.bucketOf("Download/OximeterData/"));
        assertEquals(PENDING, OximeterDataFileIndex.bucketOf("Download/OximeterData/Annotation/"));
        assertEquals(PENDING, OximeterDataFileIndex.bucketOf(null));
        assertEquals(UPLOADED, OximeterDataFileIndex.bucketOf("Download/OximeterData/Uploaded/"));
        assertEquals(UPLOADED, OximeterDataFileIndex.bucketOf("Download/OximeterData/Uploaded"));
        assertEquals(TRASH, OximeterDataFileIndex.bucketOf("Download/OximeterData/Trash/"));
        assertEquals(TRASH, OximeterDataFileIndex.bucketOf("primary:Download/OximeterData/Trash/a.csv"));
        assertEquals(TRASH, OximeterDataFileIndex.bucketOf("C:\\x\\OximeterData\\Trash\\a.csv"));
        // Tên file chứa chữ "Trash" không phải thư mục Trash
        assertEquals(PENDING, OximeterDataFileIndex.bucketOf("Download/OximeterData/TrashNote.csv"));
    }

    @Test
    public void isExpired_atExactRetentionBoundary() {
        long moved = 1_700_000_000_000L;
        assertFalse(FileRetention.isExpired(moved, moved + 15 * DAY - 1, 15));
        assertTrue(FileRetention.isExpired(moved, moved + 15 * DAY, 15));
        assertFalse(FileRetention.isExpired(moved, moved + 29 * DAY, 30));
        assertTrue(FileRetention.isExpired(moved, moved + 30 * DAY, 30));
        // Không rõ mốc -> không bao giờ xoá
        assertFalse(FileRetention.isExpired(0, moved, 15));
    }

    @Test
    public void daysLeft_roundsUp() {
        long moved = 1_700_000_000_000L;
        assertEquals(15, FileRetention.daysLeft(moved, moved, 15));
        assertEquals(15, FileRetention.daysLeft(moved, moved + 1, 15));
        assertEquals(1, FileRetention.daysLeft(moved, moved + 14 * DAY + 1, 15));
        assertEquals(0, FileRetention.daysLeft(moved, moved + 16 * DAY, 15));
    }
}
