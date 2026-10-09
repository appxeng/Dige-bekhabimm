p = 'android/app/src/main/AndroidManifest.xml'
s = open(p, encoding='utf-8').read()
perms = ['RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS', 'VIBRATE', 'WAKE_LOCK', 'FOREGROUND_SERVICE',
         'FOREGROUND_SERVICE_MICROPHONE', 'FOREGROUND_SERVICE_MEDIA_PLAYBACK', 'POST_NOTIFICATIONS',
         'USE_FULL_SCREEN_INTENT']
add = ''.join('<uses-permission android:name="android.permission.%s"/>\n    ' % x for x in perms)
s = s.replace('<application', add + '<application', 1)
comp = ('<service android:name=".MonitorService" android:exported="false" android:foregroundServiceType="microphone"/>\n'
        '        <service android:name=".AlarmService" android:exported="false" android:foregroundServiceType="mediaPlayback"/>\n'
        '        <receiver android:name=".AlarmReceiver" android:exported="false"/>\n    </application>')
s = s.replace('</application>', comp, 1)
open(p, 'w', encoding='utf-8').write(s)
print(s)
