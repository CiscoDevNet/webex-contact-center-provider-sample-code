import os

current_dir = os.path.dirname(os.path.abspath(__file__))  # This is 'main' directory
parent_dir = os.path.dirname(current_dir)  # This is the parent of 'main', which is 'project'

class AudioUtils:

    @staticmethod
    def get_default_audio():
        config_dir = os.path.join(parent_dir, "config")
        audio_dir = os.path.join(config_dir, "audio")
        with open(f"{audio_dir}/recorded_voice.wav", "rb") as audio_file:
            return audio_file.read()

