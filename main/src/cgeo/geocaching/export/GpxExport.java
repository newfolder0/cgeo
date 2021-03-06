package cgeo.geocaching.export;

import butterknife.ButterKnife;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.R;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AsyncTaskWithProgress;
import cgeo.geocaching.utils.FileUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.ShareUtils;

import org.apache.commons.lang3.CharEncoding;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GpxExport extends AbstractExport {

    public GpxExport() {
        super(getString(R.string.export_gpx));
    }

    /*
     * Overloaded export method to allow exporting a single cache by putting
     * it in an empty string array and passing that to the array export method.
     */
    @Override
    public void export(final Geocache cache, final Activity activity) {
        final String[] geocode = { cache.getGeocode() }; // get geocode, put into string array of ONE ELEMENT
        if (null == activity) {
            // No activity given, so no user interaction possible.
            // Start export with default parameters.
            new ExportTask(null).execute(geocode);

        } else {
            // Show configuration dialog
            getExportDialog(geocode, activity).show();
        }
    }

    @Override
    public void export(final List<Geocache> caches, final Activity activity) {
        final String[] geocodes = getGeocodes(caches);
        if (null == activity) {
            // No activity given, so no user interaction possible.
            // Start export with default parameters.
            new ExportTask(null).execute(geocodes);

        } else {
            // Show configuration dialog
            getExportDialog(geocodes, activity).show();
        }
    }

    private Dialog getExportDialog(final String[] geocodes, final Activity activity) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        final Context themedContext;
        if (Settings.isLightSkin() && VERSION.SDK_INT < VERSION_CODES.HONEYCOMB) {
            themedContext = new ContextThemeWrapper(activity, R.style.dark);
        } else {
            themedContext = activity;
        }

        final View layout = View.inflate(themedContext, R.layout.gpx_export_dialog, null);
        builder.setView(layout);

        final TextView text = ButterKnife.findById(layout, R.id.info);
        text.setText(getString(R.string.export_gpx_info, Settings.getGpxExportDir()));

        final CheckBox shareOption = ButterKnife.findById(layout, R.id.share);

        shareOption.setChecked(Settings.getShareAfterExport());

        shareOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Settings.setShareAfterExport(shareOption.isChecked());
            }
        });

        builder.setPositiveButton(R.string.export, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();
                new ExportTask(activity).execute(geocodes);
            }
        });

        return builder.create();
    }

    private static String[] getGeocodes(final List<Geocache> caches) {
        return Geocache.getGeocodes(caches).toArray(new String[caches.size()]);
    }

    protected class ExportTask extends AsyncTaskWithProgress<String, File> {
        private final Activity activity;

        /**
         * Instantiates and configures the task for exporting field notes.
         *
         * @param activity
         *            optional: Show a progress bar and toasts
         */
        public ExportTask(final Activity activity) {
            super(activity, getProgressTitle());
            this.activity = activity;
        }

        private File getExportFile() {
            final SimpleDateFormat fileNameDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            final Date now = new Date();
            return FileUtils.getUniqueNamedFile(Settings.getGpxExportDir() + File.separatorChar + "export_" + fileNameDateFormat.format(now) + ".gpx");
        }

        @Override
        protected File doInBackgroundInternal(final String[] geocodes) {
            // quick check for being able to write the GPX file
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                return null;
            }

            final List<String> allGeocodes = new ArrayList<>(Arrays.asList(geocodes));

            setMessage(CgeoApplication.getInstance().getResources().getQuantityString(R.plurals.cache_counts, allGeocodes.size(), allGeocodes.size()));

            final File exportFile = getExportFile();
            BufferedWriter writer = null;
            try {
                final File exportLocation = new File(Settings.getGpxExportDir());
                FileUtils.mkdirs(exportLocation);

                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(exportFile), CharEncoding.UTF_8));
                new GpxSerializer().writeGPX(allGeocodes, writer, new GpxSerializer.ProgressListener() {

                    @Override
                    public void publishProgress(final int countExported) {
                        ExportTask.this.publishProgress(countExported);
                    }
                });
            } catch (final IOException e) {
                Log.e("GpxExport.ExportTask export", e);

                if (writer != null) {
                    try {
                        writer.close();
                    } catch (final IOException ignored) {
                        // Ignore double error
                    }
                }
                // delete partial GPX file on error
                if (exportFile.exists()) {
                    FileUtils.deleteIgnoringFailure(exportFile);
                }

                return null;
            }

            return exportFile;
        }

        @Override
        protected void onPostExecuteInternal(final File exportFile) {
            if (null != activity) {
                if (exportFile != null) {
                    ActivityMixin.showToast(activity, getName() + ' ' + getString(R.string.export_exportedto) + ": " + exportFile.toString());
                    if (Settings.getShareAfterExport()) {
                        ShareUtils.share(activity, exportFile, "application/xml", R.string.export_gpx_to);
                    }
                } else {
                    ActivityMixin.showToast(activity, getString(R.string.export_failed));
                }
            }
        }

    }
}
