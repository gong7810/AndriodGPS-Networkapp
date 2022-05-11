package com.example.mylocationdata

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import android.widget.Button
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer

open class NetworkThread : Thread() {
    private var mLocation: Location? = null
    private var mIsLocationUnsynchronized: Boolean = false
    private var flag = true

    open fun requestToSynchronize(location: Location) {
        mLocation = location
        mIsLocationUnsynchronized = true
        Log.d("1", "1번째")
    }
    open fun requestToStop() {
        this.flag = false
    }

    override fun run() {
        lateinit var socket: Socket
        lateinit var socketOutputStream: DataOutputStream
        Log.d("2", "2번째")

        try {
            val ip = "203.255.57.129"
            val port = 9999

            socket = Socket(ip, port)
            socketOutputStream = DataOutputStream(socket.getOutputStream())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        while (true) {
            Log.d("3", "3번째")
            if (mIsLocationUnsynchronized) {
                if (this.flag == true){
                    Log.d("4", "4번째")
                    val buffer = ByteBuffer.allocate(24)
                    buffer.putDouble(mLocation!!.latitude)
                    buffer.putDouble(mLocation!!.longitude)
                    buffer.putDouble(mLocation!!.altitude)

                    try {
                        socketOutputStream.write(buffer.array())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if(this.flag == false){
                    val buffer = ByteBuffer.allocate(24)
                    buffer.putDouble(0.0)
                    buffer.putDouble(0.0)
                    buffer.putDouble(0.0)
                    try {
                        socketOutputStream.write(buffer.array())
                        System.runFinalization()
                        System.exit(0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            mLocation = null
            mIsLocationUnsynchronized = false
            Log.d("5", "5번째")
            sleep(10000) //샘플링 주기 = 서버에서 수신받는 주기

        }
    }
}
class MainActivity : AppCompatActivity() {

    private var mFusedLocationProviderClient: FusedLocationProviderClient? =
        null //현재위치를 가져오기 위한 변수
    private lateinit var mLastLocation: Location // 위치값을 가지고 있는 객체
    private lateinit var mLocationRequest: LocationRequest // 위치 정보 요청의 매개변수를 저장하는 변수
    private var mNetworkThread: NetworkThread? = null// 소켓통신 스레드 객체
    private val RequestPermissionLocation: Int = 10 //권한설정을 확인하기 위해 임의로 설정한 상수

    private lateinit var button: Button
    private lateinit var button2: Button
    private lateinit var text1: TextView
    private lateinit var text2: TextView
    private lateinit var text3: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

/* activity_main UI
        button = findViewById(R.id.button)
        button2 = findViewById(R.id.button2)
        text1 = findViewById(R.id.text1)
        text2 = findViewById(R.id.text2)
        text3 = findViewById(R.id.text3)
*/
        button = findViewById(R.id.call)
        button2 = findViewById(R.id.back)

        mLocationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000 //화면에 표시되는 위치 갱신주기 단위는 ms
        }

        //버튼 이벤트를 통해 현재 위치
        button.setOnClickListener {
            if (checkPermissionForLocation(this)) {
                startLocationUpdates()
            }
        }

        button2.setOnClickListener{
            if(checkPermissionForLocation(this)){
                mNetworkThread!!.requestToStop()
            }
        }
    }

    private fun startLocationUpdates() {

        //FusedLocationProviderClient의 인스턴스를 생성.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // 기기의 위치에 관한 정기 업데이트를 요청하는 메서드 실행
        // 지정한 루퍼 스레드(Looper.myLooper())에서 콜백(mLocationCallback)으로 위치 업데이트를 요청
        mFusedLocationProviderClient!!.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()!!
        )
    }

    // 시스템으로 부터 위치 정보를 콜백으로 받음
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // 시스템에서 받은 location 정보를 onLocationChanged()에 전달
            onLocationChanged(locationResult.lastLocation)
        }
    }

    // 시스템으로 부터 받은 위치정보를 화면에 갱신해주는 메소드
    fun onLocationChanged(location: Location) {
        mLastLocation = location
        /*  지도를 뺌으로 삭제
        text1.text = "위도 : " + mLastLocation.latitude // 갱신 된 위도
        text2.text = "경도 : " + mLastLocation.longitude // 갱신 된 경도
        text3.text = "고도 : " + mLastLocation.altitude // 갱신 된 고도
        */
        if (mNetworkThread == null) { //초기화 되있기때문에 if문 통과
            mNetworkThread = NetworkThread()
            mNetworkThread!!.start()
        }

        mNetworkThread!!.requestToSynchronize(mLastLocation) //스레드 주기 5,097ms
    }


    // 위치 권한이 있는지 확인하는 메서드
    private fun checkPermissionForLocation(context: Context): Boolean {
        // Android 6.0 Marshmallow 이상에서는 위치 권한에 추가 런타임 권한이 필요
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                // 권한이 없으므로 권한 요청 알림 보내기
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    RequestPermissionLocation
                )
                false
            }
        } else {
            true
        }
    }

    // 사용자에게 권한 요청 후 결과에 대한 처리 로직
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RequestPermissionLocation) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()

            } else {
                Log.d("ttt", "onRequestPermissionsResult() _ 권한 허용 거부")
                Toast.makeText(this, "권한이 없어 해당 기능을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
