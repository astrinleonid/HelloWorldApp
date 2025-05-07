package com.example.helloworldapp


//class PlayRecordsActivity : AppCompatActivity() {
//    private lateinit var listView: ListView
//    private lateinit var textViewStatus: TextView
//    private var recordId = ""
//    private lateinit var okButton: Button
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_file_list)
//
//        listView = findViewById(R.id.listView)
//        textViewStatus = findViewById(R.id.textViewStatus)
//        okButton = findViewById(R.id.buttonOK)
//        recordId = intent.getStringExtra("UNIQUE_ID") ?: ""
//
//        // Load recordings using RecordManager
//        loadRecordings()
//
//        okButton.setOnClickListener {
//            // Show confirmation dialog instead of simply finishing
//            DialogUtils.showSaveOptionsDialog(this, recordId)
//        }
//    }
//
//    private fun loadRecordings() {
//        if (AppConfig.online) {
//            // Use RecordManager to sync with server first
//            RecordManager.syncWithServer(recordId) { success ->
//                runOnUiThread {
//                    if (success) {
//                        displayRecordings()
//                    } else {
//                        textViewStatus.text = "Error loading recordings from server"
//                        setupListView(emptyList())
//                    }
//                }
//            }
//        } else {
//            // Just display any locally recorded points
//            displayRecordings()
//        }
//    }
//
//    private fun displayRecordings() {
//        // Get recorded points from RecordManager
//        val recordedPoints = RecordManager.getAllPointRecords(recordId)
//            ?.filter { it.value.isRecorded }
//            ?.map { it.key.toString() }
//            ?: emptyList()
//
//        if (recordedPoints.isEmpty()) {
//            textViewStatus.text = "No recordings found"
//        } else {
//            textViewStatus.text = "${recordedPoints.size} recordings available"
//        }
//
//        setupListView(recordedPoints)
//    }
//
//    private fun setupListView(recordedPoints: List<String>) {
//        val adapter = FileListAdapter(
//            this,
//            recordedPoints.toMutableList(),
//            recordId,
//            textViewStatus
//        ) {
//            // Reload after changes
//            loadRecordings()
//        }
//
//        listView.adapter = adapter
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        RecordManager.releaseMediaPlayer()
//        setResult(Activity.RESULT_OK)
//    }
//}