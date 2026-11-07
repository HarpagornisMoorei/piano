package com.example.myapplication
import android.content.Context
import androidx.core.app.NotificationCompat
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.app.NotificationManager
import android.Manifest.permission.POST_NOTIFICATIONS
class NotificationService(
    private val context: Context //need to get acces to notficationmanager which we need to show notfication
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    fun SHOWNOTIFICATION(){ //show notification
        var ACTIVITYINTENT = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        var ACTIVITYPENDINGINTENT = PendingIntent.getActivity(
            context,
            1,
            ACTIVITYINTENT,
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                PendingIntent.FLAG_IMMUTABLE
            } else 0
        )
        val NOTIFICATION = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_baby_changing_station_24)
            .setContentTitle("conversion finished!")
            .setContentText("come back to daddy")
            .setContentIntent(ACTIVITYPENDINGINTENT) //sends intent when user clicks
            .setAutoCancel(true)
            .build()
        notificationManager.notify(
            1, NOTIFICATION
        )



    }
    companion object{
        const val CHANNEL_ID ="channel"
    }
}