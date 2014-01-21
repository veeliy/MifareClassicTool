/*
 * Copyright 2013 Gerhard Klostermeier
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package de.syss.MifareClassicTool.Activities;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;
import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;

/**
 * A simple hex editor for showing and editing tag dumps generated by the
 * {@link ReadTag}. Features are:
 * <ul>
 * <li>Color<ul>
 *  <li>keys (A/B)</li>
 *  <li>Access Conditions</li>
 *  <li>Value Blocks</li>
 *  <li>UID</li>
 *  <li>Manuf. Info</li>
 * </ul></li>
 * <li>Save dump to file</li>
 * <li>Display dump as US-ASCII</li>
 * <li>Display Access Conditions as table</li>
 * <li>Resolve Value Blocks to integers</li>
 * </ul>
 * @author Gerhard Klostermeier
 */
public class DumpEditor extends BasicActivity {

    // LOW: Pass a better object then a stringblobb separated by new line.
    // (See http://stackoverflow.com/a/2141166)

    /**
     * A dump separated by new lines. Headers (e.g. "Sector 01") are marked
     * with a "+"-symbol (e.g. "+Sector 01"). Errors (e.g. "No keys found or
     * dead sector") are marked with a "*"-symbol.
     */
    public final static String EXTRA_DUMP =
            "de.syss.MifareClassicTool.Activity.DUMP";

    private static final String LOG_TAG =
            DumpEditor.class.getSimpleName();

    private LinearLayout mLayout;
    private String mFileName = "";

    /**
     * All blocks containing valid data AND their headers (marked with "+"
     * e.g. "+Sector: 0") as strings.
     * This will be updated with every {@link #isValidDump()}
     * check.
     */
    private String[] mLines;

    /**
     * Check whether to initialize the editor on a dump file or on
     * a new dump directly from {@link ReadTag}
     * (or recreate instance state if the activity was killed).
     * Also it will color the caption of the dump editor.
     * @see #initEditor(String[])
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_editor);

        mLayout= (LinearLayout) findViewById(
                R.id.LinearLayoutDumpEditor);

        // Color caption.
        SpannableString keyA = Common.colorString(
                getString(R.string.text_keya),
                getResources().getColor(R.color.light_green));
        SpannableString keyB =  Common.colorString(
                getString(R.string.text_keyb),
                getResources().getColor(R.color.dark_green));
        SpannableString ac = Common.colorString(
                getString(R.string.text_ac),
                getResources().getColor(R.color.orange));
        SpannableString uidAndManuf = Common.colorString(
                getString(R.string.text_uid_and_manuf),
                getResources().getColor(R.color.purple));
        SpannableString vb = Common.colorString(
                getString(R.string.text_valueblock),
                getResources().getColor(R.color.yellow));

        TextView caption = (TextView) findViewById(
                R.id.textViewDumpEditorCaption);
        caption.setText(TextUtils.concat(uidAndManuf, " | ",
                vb, " | ", keyA, " | ", keyB, " | ", ac), BufferType.SPANNABLE);
        // Add web-link optic to update colors text view (= caption title).
        TextView captionTitle = (TextView) findViewById(
                R.id.textViewDumpEditorCaptionTitle);
        SpannableString updateText = Common.colorString(
                getString(R.string.text_update_colors),
                getResources().getColor(R.color.blue));
        updateText.setSpan(new UnderlineSpan(), 0, updateText.length(), 0);
        captionTitle.setText(TextUtils.concat(
                getString(R.string.text_caption_title),
                ": (", updateText, ")"));

        if (getIntent().hasExtra(EXTRA_DUMP)) {
            // Called from ReadTag (init editor by intent).
            String dump = getIntent().getStringExtra(EXTRA_DUMP);
            // Set title with UID.
            if (Common.getUID() != null) {
                setTitle(getTitle() + " (UID: " + Common.byte2HexString(
                        Common.getUID())+ ")");
            }
            String[] lines = dump.split(System.getProperty("line.separator"));
            initEditor(lines);
            setIntent(null);
        } else if (getIntent().hasExtra(
                FileChooser.EXTRA_CHOSEN_FILE)) {
            // Called form FileChooser (init editor by file).
            File file = new File(getIntent().getStringExtra(
                    FileChooser.EXTRA_CHOSEN_FILE));
            mFileName = file.getName();
            setTitle(getTitle() + " (" + mFileName + ")");
            initEditor(Common.readFileLineByLine(file, false, this));
            setIntent(null);
        } else if (savedInstanceState != null) {
            // Recreated after kill by Android (due to low memory).
            mFileName = savedInstanceState.getString("file_name");
            if (mFileName == null) {
                mFileName = "";
            } else {
                setTitle(getTitle() + " (" + mFileName + ")");
            }
            mLines = savedInstanceState.getStringArray("lines");
            if (mLines != null) {
                initEditor(mLines);
            }
        }
    }

    /**
     * Add a menu with editor functions to the Activity.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.dump_editor_functions, menu);
        return true;
    }

    /**
     * Save {@link #mLines} and {@link #mFileName}.
     */
    @Override
    public void onSaveInstanceState (Bundle outState) {
        outState.putStringArray("lines", mLines);
        outState.putString("file_name", mFileName);
    }

    /**
     * Handle the selected function from the editor menu.
     * @see #saveDump()
     * @see #shareDump()
     * @see #showAscii()
     * @see #showAC()
     * @see #decodeValueBlocks()
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
        case R.id.menuDumpEditorSave:
            saveDump();
            return true;
        case R.id.menuDumpEditorAscii:
            showAscii();
            return true;
        case R.id.menuDumpEditorAccessConditions:
            showAC();
            return true;
        case R.id.menuDumpEditorValueBlocksAsInt:
            decodeValueBlocks();
            return true;
        case R.id.menuDumpEditorShare:
            shareDump();
            return true;
        case R.id.menuDumpEditorOpenValueBLockTool:
            openValueBlockTool();
            return true;
        case R.id.menuDumpEditorOpenAccessConditionTool:
            openAccessConditionTool();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Update the coloring. This method updates the colors if all
     * data are valid {@link #isValidDumpErrorToast()}.
     * To do so, it reinitializes the whole editor... not quite beautiful.
     * @param view The View object that triggered the method
     * (in this case the update color text (color caption text)).
     * @see #isValidDumpErrorToast()
     * @see #initEditor(String[])
     */
    public void onUpdateColors(View view) {
        if (isValidDumpErrorToast()) {
            // Backup focused view.
            View focused = mLayout.getFocusedChild();
            int focusIndex = -1;
            if (focused != null) {
                focusIndex = mLayout.indexOfChild(focused);
            }
            initEditor(mLines);
            if (focusIndex != -1) {
                // Restore focused view.
                while (focusIndex >= 0
                        && mLayout.getChildAt(focusIndex) == null) {
                    focusIndex--;
                }
                if (focusIndex >= 0) {
                    mLayout.getChildAt(focusIndex).requestFocus();
                }
            }
        }
    }

    /**
     * Check if it is a valid dump ({@link #isValidDump()}),
     * ask user for a save name and then call
     * {@link Common#saveFile(File, String[])}
     * with {@link #mLines}.
     * @see #isValidDump()
     * @see #isValidDumpErrorToast()
     * @see Common#saveFile(File, String[])
     */
    private void saveDump() {
        if (isValidDumpErrorToast()) {
            if (!Common.isExternalStorageWritableErrorToast(this)) {
                return;
            }
            final File path = new File(
                    Environment.getExternalStoragePublicDirectory(
                    Common.HOME_DIR) +  "/" + Common.DUMPS_DIR);
            final Context cont = this;
            // Ask user for filename.
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setLines(1);
            input.setHorizontallyScrolling(true);
            input.setText(mFileName);
            input.setSelection(input.getText().length());
            new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_save_dump_title)
                .setMessage(R.string.dialog_save_dump)
                .setIcon(android.R.drawable.ic_menu_save)
                .setView(input)
                .setPositiveButton(R.string.action_save,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (input.getText() != null
                                && !input.getText().toString().equals("")) {
                            File file = new File(path.getPath(),
                                    input.getText().toString());
                            if (Common.saveFile(file, mLines)) {
                                Toast.makeText(cont,
                                        R.string.info_save_successful,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(cont,
                                        R.string.info_save_error,
                                        Toast.LENGTH_LONG).show();
                            }
                            onUpdateColors(null);
                        } else {
                            // Empty name is not allowed.
                            Toast.makeText(cont, R.string.info_empty_file_name,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.action_cancel,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing.
                    }
                }).show();
        }
    }

    /**
     * Check all blocks if they contain valid data. If all blocks are O.K.
     * {@link #mLines} will be updated.
     * @return <ul>
     * <li>0 - All blocks are O.K.</li>
     * <li>1 - At least one sector has not 4 blocks (lines).</li>
     * <li>2 - At least one block has invalid characters (not hex or "-" as
     * marker for no key/no data).</li>
     * <li>3 - At least one block has not 16 byte (32 chars).</li>
     * </ul>
     * @see #mLines
     */
    private int isValidDump() {
        ArrayList<String> checkedLines = new ArrayList<String>();
        for(int i = 0; i < mLayout.getChildCount(); i++){
            View child = mLayout.getChildAt(i);
            if (child instanceof EditText) {
                String[] lines = ((EditText)child).getText().toString()
                        .split(System.getProperty("line.separator"));
                if (lines.length != 4 && lines.length != 16) {
                    // Not 4 or 16 lines.
                    return 1;
                }
                for (int j = 0; j < lines.length; j++) {
                    // Is hex or "-" == NO_KEY or NO_DATA.
                    if (lines[j].matches("[0-9A-Fa-f-]+") == false) {
                        // Not pure hex.
                        return 2;
                    }
                    if (lines[j].length() != 32) {
                        // Not 32 chars per line.
                        return 3;
                    }
                    lines[j] = lines[j].toUpperCase(Locale.getDefault());
                    checkedLines.add(lines[j]);
                }
            } else if (child instanceof TextView) {
                TextView tv = (TextView) child;
                String tag = (String) tv.getTag();
                // Only save real headers (not the headers
                // of sectors with "no keys found or dead sector" error).
                if (tag != null && tag.equals("real_header")) {
                    // Mark headers (sectors) with "+"
                    checkedLines.add("+Sector: "
                            + tv.getText().toString().split(": ")[1]);
                }
            }
        }
        // Update mLines.
        mLines = checkedLines.toArray(new String[checkedLines.size()]);
        return 0;
    }

    /**
     * Check dump with {@link #isValidDump()} and show
     * a Toast message with error informations (if an error occurred).
     * @return True if all blocks were O.K.. False otherwise.
     */
    private boolean isValidDumpErrorToast() {
        int err = isValidDump();
        if (err == 1) {
            Toast.makeText(this, R.string.info_valid_dump_not_4_or_16_lines,
                    Toast.LENGTH_LONG).show();
        } else if (err == 2) {
            Toast.makeText(this, R.string.info_valid_dump_not_hex,
                    Toast.LENGTH_LONG).show();
        } else if (err == 3) {
            Toast.makeText(this, R.string.info_valid_dump_not_16_bytes,
                    Toast.LENGTH_LONG).show();
        }
        return err == 0;
    }

    /**
     * Initialize the editor with the given lines. If the lines do not contain
     * a valid dump, an error Toast will be shown and the Activity exits.
     * @param lines Block data and header (e.g. "sector: 0"). Minimum is one
     * Sector (5 Lines, 1 Header + 4 Hex block data).
     * @see #isValidDumpErrorToast()
     * @see #isValidDump()
     */
    private void initEditor(String[] lines) {
        boolean err = false;
        if (lines != null && lines[0].startsWith("+")) {
            // Parse dump and show it.
            mLayout.removeAllViews();
            boolean isFirstBlock = false;
            int blockCounter = 0;
            EditText et = null;
            ArrayList<SpannableString> blocks =
                    new ArrayList<SpannableString>(4);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith("+")) {
                    // Line is a header.
                    isFirstBlock = lines[i].endsWith(" 0");
                    String sectorNumber = lines[i].split(": ")[1];
                    // Add sector header (TextView).
                    TextView tv = new TextView(this);
                    tv.setTextColor(
                            getResources().getColor(R.color.blue));
                    tv.setText(getString(R.string.text_sector) +
                            ": " + sectorNumber);
                    mLayout.addView(tv);
                    // Add sector data (EditText) if not at the end and if the
                    // next line is not an error line ("*").
                    if (i+1 != lines.length && !lines[i+1].startsWith("*")) {
                        // Add sector data (EditText).
                        et = new EditText(this);
                        et.setLayoutParams(new LayoutParams(
                                LayoutParams.WRAP_CONTENT,
                                LayoutParams.WRAP_CONTENT));
                        et.setInputType(et.getInputType()
                                |InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                |InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                        et.setTypeface(Typeface.MONOSPACE);
                        // Set text size of an EditText to the text size of
                        // a TextView. (getTextSize() returns
                        // pixels - unit is needed.)
                        et.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                                new TextView(this).getTextSize());
                        mLayout.addView(et);
                        // Tag headers of real sectors (sectors containing
                        // data (EditText) and not errors ("*")).
                        tv.setTag("real_header");
                    }
                } else if (lines[i].startsWith("*")){
                    // Error Line: Line is a sector that could not be read.
                    TextView tv = new TextView(this);
                    tv.setTextColor(
                            getResources().getColor(R.color.red));
                    tv.setText("   " +  getString(
                            R.string.text_no_key_io_error));
                    tv.setTag("error");
                    mLayout.addView(tv);
                } else {
                    // Line is a block.
                    if (i+1 == lines.length || lines[i+1].startsWith("+")) {
                        // Add sector trailer.
                        blocks.add(colorSectorTrailer(lines[i]));
                        blockCounter++;
                        // Add sector data to the EditText, if there
                        // are 4 or 16 blocks.
                        if (blockCounter == 4 || blockCounter == 16) {
                            CharSequence text = "";
                            int j;
                            for (j = 0; j < blocks.size()-1; j++) {
                                text = TextUtils.concat(
                                        text, blocks.get(j), "\n");
                            }
                            text = TextUtils.concat(text, blocks.get(j));
                            et.setText(text, BufferType.SPANNABLE);
                            blocks = new ArrayList<SpannableString>(4);
                            blockCounter = 0;
                        } else {
                            err = true;
                            break;
                        }
                    } else {
                        // Add data block.
                        blocks.add(colorDataBlock(lines[i], isFirstBlock));
                        isFirstBlock = false;
                        blockCounter++;
                    }
                }
            }
            if (!isValidDumpErrorToast()) {
                err = true;
            }
        } else {
            err = true;
        }
        if (err == true) {
            mLayout.removeAllViews();
            Toast.makeText(this, R.string.info_editor_init_error,
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Display the the hex data as US-ASCII ({@link HexToAscii}).
     * @see HexToAscii
     */
    private void showAscii() {
        if (isValidDumpErrorToast()) {
            String dump = "";
            String s = System.getProperty("line.separator");
            for (int i = 0; i < mLines.length-1; i++) {
                if (i+1 == mLines.length
                        || mLines[i+1].startsWith("+")) {
                    // Skip Access Conditions.
                    dump += s;
                    continue;
                }
                dump += mLines[i] + s;
            }
            Intent intent = new Intent(this, HexToAscii.class);
            intent.putExtra(EXTRA_DUMP, dump);
            startActivity(intent);
        }
    }

    /**
     * Display the access conditions {@link AccessConditionDecoder}.
     * @see AccessConditionDecoder
     */
    private void showAC() {
        if (isValidDumpErrorToast()) {
            String ac = "";
            int lastSectorHeader = 0;
            String s = System.getProperty("line.separator");
            for (int i = 0; i < mLines.length; i++) {
                if (mLines[i].startsWith("+")) {
                    // Header.
                    ac += mLines[i] + s;
                    lastSectorHeader = i;
                } else if (i+1 == mLines.length
                        || mLines[i+1].startsWith("+")) {
                    // Access Condition.
                    if (i - lastSectorHeader > 4) {
                        // Access Conditions of a sector
                        // with more than 4 blocks --> Mark ACs with "*".
                        ac += "*";
                    }
                    ac += mLines[i].substring(12, 20) + s;
                }
            }
            Intent intent = new Intent(this, AccessConditionDecoder.class);
            intent.putExtra(AccessConditionDecoder.EXTRA_AC, ac);
            startActivity(intent);
        }
    }

    /**
     * Display the value blocks as integer ({@link ValueBlocksToInt}).
     * @see ValueBlocksToInt
     */
    private void decodeValueBlocks() {
        if (isValidDumpErrorToast()) {
            String vb = "";
            String header = "";
            int blockCounter = 0;
            String s = System.getProperty("line.separator");
            for (int i = 0; i < mLines.length; i++) {
                if (mLines[i].startsWith("+")) {
                    header = mLines[i];
                    blockCounter = 0;
                    continue;
                } else {
                    if (Common.isValueBlock(mLines[i])) {
                        // Header.
                        vb += header + ", Block: " + blockCounter + s;
                        // Value Block.
                        vb += mLines[i] + s;
                    }
                    blockCounter++;
                }
            }
            Intent intent = new Intent(this, ValueBlocksToInt.class);
            if (!vb.equals("")) {
                intent.putExtra(ValueBlocksToInt.EXTRA_VB, vb);
                startActivity(intent);
            } else {
                // No value blocks found.
                Toast.makeText(this, R.string.info_no_vb_in_dump,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Open the Value Block decoder/encoder ({@link ValueBlockTool}).
     * @see ValueBlockTool
     */
    private void openValueBlockTool() {
        Intent intent = new Intent(this, ValueBlockTool.class);
        startActivity(intent);
    }

    /**
     * Open the Access Condition decoder/encoder ({@link AccessConditionTool}).
     * @see AccessConditionTool
     */
    private void openAccessConditionTool() {
        Intent intent = new Intent(this, AccessConditionTool.class);
        startActivity(intent);
    }

    /**
     * Share a dump as "file://" stream resource (e.g. as e-mail attachment).
     * The dump will be checked and stored in the {@link Common#TMP_DIR}
     * directory. After this, a dialog will be displayed in which the user
     * can choose between apps that are willing to handle the dump.
     * @see #isValidDumpErrorToast()
     * @see Common#TMP_DIR
     */
    private void shareDump() {
        // Save dump to to a temporary file which will be
        // attached for sharing (and stored in the tmp folder).
        String fileName;
        if (mFileName.equals("")) {
            // The dump has no name. Use date and time as name.
            Time today = new Time(Time.getCurrentTimezone());
            today.setToNow();
            fileName = today.format("%Y-%m-%d-%H-%M-%S");
        } else {
            fileName = mFileName;
        }
        // Save file to tmp directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Common.HOME_DIR) + "/" + Common.TMP_DIR, fileName);
        Common.saveFile(file, mLines);

        // Share file.
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(
                "file://" + file.getAbsolutePath()));
        sendIntent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.text_share_subject_dump)
                + " (" + fileName + ")");
        startActivity(Intent.createChooser(sendIntent,
                getText(R.string.dialog_share_title)));
    }

    /**
     * Create a full colored string (representing one block).
     * @param data Block data as hex string (16 Byte, 32 Chars.).
     * @param hasUID True if the block is the first block of the entire tag
     * (Sector 0, Block 0).
     * @return A full colored string.
     */
    private SpannableString colorDataBlock(String data, boolean hasUID) {
        SpannableString ret = null;
        if (hasUID) {
            // First block (UID, manuf. data).
            ret = new SpannableString(TextUtils.concat(
                    Common.colorString(data,
                            getResources().getColor(R.color.purple))));
        } else {
            if (Common.isValueBlock(data)) {
                // Value block.
                ret = Common.colorString(data,
                        getResources().getColor(R.color.yellow));
            } else {
                // Just data.
                ret = new SpannableString(data);
            }
        }
        return ret;
    }

    /**
     * Create a full colored sector trailer (representing the last block of
     * every sector).
     * @param data Block data as hex string (16 Byte, 32 Chars.).
     * @return A full colored string.
     */
    private SpannableString colorSectorTrailer(String data) {
        // Get sector trailer colors.
        int colorKeyA = getResources().getColor(
                R.color.light_green);
        int colorKeyB = getResources().getColor(
                R.color.dark_green);
        int colorAC = getResources().getColor(
                R.color.orange);
        try {
            SpannableString keyA = Common.colorString(
                    data.substring(0, 12), colorKeyA);
            SpannableString keyB = Common.colorString(
                    data.substring(20), colorKeyB);
            SpannableString ac = Common.colorString(
                    data.substring(12, 20), colorAC);
            return new SpannableString(
                    TextUtils.concat(keyA, ac, keyB));
        } catch (IndexOutOfBoundsException e) {
            Log.d(LOG_TAG, "Error while coloring " +
                    "sector trailer");
        }
        return new SpannableString(data);
    }
}
