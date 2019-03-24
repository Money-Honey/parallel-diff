package com.vd.diff;

import com.vd.diff.commandline.CmdUtils;
import com.vd.diff.compare.DiffService;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class EntryPoint {

    public static void main(String[] args) throws IOException {
        CommandLine cmd = CmdUtils.getCmdOrExitWithHint(args);

        String inLeftFilePath = cmd.getOptionValue("i1");
        String inRightFilePath = cmd.getOptionValue("i2");
        String outFilePath = cmd.getOptionValue("output");

        File fileA = new File(inLeftFilePath);
        File fileB = new File(inRightFilePath);

        File diff = new DiffService().diffFiles(
                fileA,
                fileB,
                StringUtils.firstNonBlank(outFilePath, "eventsDiff.txt")
        );
        log.info("The diff file path is: {}. Size of file is: {}", diff.getAbsolutePath(), diff.length());

        /*
         * make a diffEvents in parallel what involves tricky manipulations by splitting files
         * 1) get first, last keys from files
         * 2) find start_key, stop_key as higher and lower for both files
         * 3) split the range (start_key - stop_key) by (num of processors - 1) * 10 for example
         *  to split there is some algorithm implementation from hbase core to receive start/stop keys for each split
         * 4) split both input files by dividing their sizes by (num of processors - 1) * 10 for example
         *  it's a rough slip which should be continued by seeking to start or end of line (all records are splited by \n).
         * 5) Create parallel task to evaluate the diffEvents
         *  by going through the range splits and find splits from both files which fit the range.
         * 5.1) It's important to have file model with type A, B or Left, Right to understand which exactly strategy should be applied
         *  - if the is no slip from the left -> all records from the right are new and vice versa
         * 6) During the diffEvents task we always need to skip all records which don't belong to the range split
         *  (some records close to start and close to the end)
         * 7) During the reading files using `RandomAccessFile` class it's expected to make the diffEvents in the memory
         *  buffer and upon the buffer overflows the buffer should be flushed to the disk
         *  It's important to save files with split's name like (1,2,3,4,5).txt.
         *  Then these files i think might be able to be joined without reading with O(f) not O(n)
         *  where n (number of records inside the diffs) and f (number of files to be joined)
         * 8) Check if ForkJoin algorithm and RecursiveTask might be able to be used.
         *
         *  _FILE_A______________VIRTUAL_
         * |____1___|         |_____1____|          _FILE_B_
         * |___13___|         |____10____|         |___45___|
         * |___25___|         |____20____|         |___54___|
         * |___37___|         |____30____|         |___58___|
         * |___49___|         |____40____|         |___62___|
         * |___51___|         |____50____|         |___67___|
         * |___55___|         |____60____|         |___71___|
         * |___61___|         |____70____|         |___75___|
         * |___78___|         |____80____|         |___78___|
         * |___87___|         |____90____|         |___91___|
         *                    |____99____|_________|___99___|
         *
         * Next pairs will be created for virtual splits range:
         * 1.  1-10:  A(1-13)                       : 1-10 records will be flushed as NEW. 10-13 will be skipped
         * 2.  10-20: A(1-13) + (13-25)             : 1-10 records will be skipped. 10-20 marked as as NEW. 20-25 will be skipped
         * 3.  20-30: A(13-25) + (25-37)            : 13-20 records will be skipped. 20-30 marked as as NEW. 30-37 will be skipped
         * 4.  30-40: A(25-37) + (37-49)            : 25-30 records will be skipped. 30-40 marked as as NEW. 40-49 will be skipped
         * Interesting starts here :)
         * 5.  40-50: A(37-49) + (49-51) + B(45-54) : 37-40 records will be skipped. 40-50 will be compared. 50-51(A) and 50-54(B) will be skipped
         * 6.  50-60: A(49-61)           + B(45-62) : 49-50(A) + 45-50(B) will be skipped. 50-60 will be compared. 60-61(A) and 60-62(B) will be skipped
         * 7.  60-70: A(55-78)           + B(58-71) : 55-60(A) + 58-60(B) will be skipped. 60-70 will be compared. 70-78(A) and 70-71(B) will be skipped
         * 8.  70-80: A(61-87)           + B(67-91) : 61-70(A) + 67-70(B) will be skipped. 70-80 will be compared. 80-87(A) and 70-91(B) will be skipped
         * 9.  80-90: A(78-87)           + B(78-91) : 78-80(A) + 78-80(B) will be skipped. 80-90 will be compared. 90-91(B) will be skipped
         * 10. 90-99:                      B(78-99) : 90-99(B) will be marked as NEW.
         *
         *
         * Hence, looks this algorithm should work in parallel as well
         * */

        /*
         * Finally both approaches should be measured by time, and results with diffs should be compared
         * */
    }
}