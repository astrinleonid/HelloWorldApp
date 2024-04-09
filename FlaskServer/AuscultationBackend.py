from flask import Flask, request, jsonify
import os
from werkzeug.utils import secure_filename
import librosa  # You might need to install this library for handling audio files
from datetime import date
import random



app = Flask(__name__)

# This is the path where uploaded files will be stored
UPLOAD_FOLDER = '/path/to/the/uploads'
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

class Record:

    def __init__(self, id):
        self.id = id
        self.chunks = []
        self.chunk_quality = []
        self.successful = False

    def check_sucsess(self):
        self.successful = '111' in "".join(self.chunk_quality)
        return self.successful

records = {}



@app.route('/process-audio', methods=['POST'])
def process_audio():
    # Check if the post request has the file part
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400
    file = request.files['file']
    # If the user does not select a file, the browser submits an
    # empty file without a filename.
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400
    if file:
        filename = secure_filename(file.filename)
        file_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        file.save(file_path)

        # Process the audio file
        result = process_file(file_path)

        # Remove the file after processing
        os.remove(file_path)

        return jsonify({"result": result})

UPLOAD_FOLDER = 'uploads'
ALLOWED_EXTENSIONS = {'wav', 'mp3', '3gp', 'aac', 'flac'}

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB limit

# Ensure the upload folder exists
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


@app.route('/getUniqueId', methods=['POST'])  # Change to POST to accept data
def get_unique_id():
    # Extract maker and model from the posted data
    data = request.get_json()  # Parse JSON payload
    maker = data.get('maker')
    model = data.get('model')

    print(f"Maker: {maker}, Model: {model}")  # Print maker and model

    # Generate a unique ID - here, we use a UUID for simplicity
    unique_id = generate_ID(6)
    records[unique_id] = Record(unique_id)
    print(f"ID generated: {unique_id}")

    return jsonify(unique_id)




@app.route('/upload', methods=['POST'])
def upload_file():


    # Check if the post request has the file part
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400
    file = request.files.get('file')
    button_number = request.form.get('button_number')
    record_quality = random.choice(['0','1'])
    ID = request.form.get('record_id')
    print(f"request received ID {ID} button {button_number} response {record_quality}")
    if ID not in records:
        records[ID] = Record(ID)
    record = records[ID]

    # If the user does not select a file, the browser submits an empty file without a filename
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400
    if file and allowed_file(file.filename):
        # filename = secure_filename(file.filename)
        filename = f"record{date.today()}ID{ID}no{len(record.chunks)}.3gp"
        save_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        file.save(save_path)
        record.chunks.append(save_path)
        record.chunk_quality.append(record_quality)
        if record.check_sucsess():
            return jsonify({"message": "Record sucsessfull", "filename": filename})
        else:
            return jsonify({"message": "Continue", "filename": filename})
    else:
        return jsonify({"error": "File type not allowed"}), 400


def process_file(file_path):
    """Simple processing function to check if the audio is longer than 5 seconds."""
    # Load the audio file
    audio, sr = librosa.load(file_path, sr=None)
    duration = librosa.get_duration(y=audio, sr=sr)
    # Check if duration is greater than 5 seconds
    if duration > 5:
        return 1
    else:
        return 0

import random
import string

def generate_ID(seq_length):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=seq_length)).lower()




if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)