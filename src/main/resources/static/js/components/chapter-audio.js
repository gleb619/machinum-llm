/**
 * Creates an Alpine.js data object with audio functionality
 */
export function audioApp() {
    return {
        audioSelectedId: '',
        audioFiles: [],
        audioLoading: false,
        audioError: null,
        audioCurrentTime: 0,
        audioDuration: 0,
        audioVolume: 50,
        audioIsPaused: true,
        audioExpandedMetadata: '',
        minioEndpoint: '',
        minioBucket: '',

        get audioProgressPercent() {
            return this.audioDuration > 0 ? (this.audioCurrentTime / this.audioDuration) * 100 : 0;
        },

        async initAudio() {
            await this.loadAudioFiles();
            const endpointMeta = document.querySelector('meta[name=mt-minio-endpoint]').getAttribute('content');
            const bucketMeta = document.querySelector('meta[name=mt-minio-bucket]').getAttribute('content');

            if(endpointMeta) {
                this.minioEndpoint = atob(endpointMeta);
            }
            if(bucketMeta) {
                this.minioBucket = atob(bucketMeta);
            }
        },

        async loadAudioFiles() {
            if(!this.activeId) return;
            this.audioLoading = true;
            this.audioError = null;

            try {
                const response = await fetch(`/api/chapters/${this.activeId}/audio`);
                if (!response.ok) {
                    throw new Error(`HTTP audioError! status: ${response.status}`);
                }
                this.audioFiles = await response.json();
            } catch (err) {
                this.audioError = err.message || 'Failed to load audio files';
                console.error('Error loading audio files:', err);
            } finally {
                this.audioLoading = false;
            }
        },

        async toggleAudio(audioId) {
            if (this.audioSelectedId === audioId) {
                this.togglePlayPause();
            } else {
                await this.playAudio(audioId);
            }
        },

        async playAudio(audioId) {
            const audioPlayer = this.$refs.audioPlayer;
            
            // Stop current audio if playing
            if (!audioPlayer.paused) {
                audioPlayer.pause();
            }

            this.audioSelectedId = audioId;
            audioPlayer.src = `/api/audio/${audioId}/content`;
            audioPlayer.audioVolume = this.audioVolume / 100;
            
            try {
                await audioPlayer.play();
                this.audioUpdateDuration();
                this.audioUpdateTime();
                this.audioIsPaused = false;
            } catch (err) {
                this.audioError = 'Failed to play audio file';
                console.audioError('audioError playing audio:', err);
            }
        },

        togglePlayPause() {
            const audioPlayer = this.$refs.audioPlayer;
            if (audioPlayer.paused) {
                audioPlayer.play();
                this.audioIsPaused = false;
            } else {
                audioPlayer.pause();
                this.audioIsPaused = true;
            }
        },

        seekTo(time) {
            const audioPlayer = this.$refs.audioPlayer;
            audioPlayer.audioCurrentTime = time;
        },

        seekRelative(seconds) {
            const audioPlayer = this.$refs.audioPlayer;
            audioPlayer.audioCurrentTime = Math.max(0, Math.min(audioPlayer.audioDuration, audioPlayer.audioCurrentTime + seconds));
        },

        setVolume(audioVolume) {
            this.audioVolume = audioVolume;
            const audioPlayer = this.$refs.audioPlayer;
            audioPlayer.audioVolume = audioVolume / 100;
        },

        audioUpdateTime() {
            const audioPlayer = this.$refs.audioPlayer;
            this.audioCurrentTime = audioPlayer.currentTime;
        },

        audioUpdateDuration() {
            const audioPlayer = this.$refs.audioPlayer;
            this.audioDuration = audioPlayer.duration;
        },

        onAudioEnded() {
            this.audioIsPaused = true;
            this.audioCurrentTime = 0;
        },

        getAudioTitle(audioFile) {
            return audioFile.id.substring(0, 8);
        },

        audioFormatDate(dateString) {
            return new Date(dateString).toLocaleDateString();
        },

        audioFormatTime(seconds) {
            if (isNaN(seconds)) return '0:00';
            const mins = Math.floor(seconds / 60);
            const secs = Math.floor(seconds % 60);
            return `${mins}:${secs.toString().padStart(2, '0')}`;
        },

        audioFormatDateTime(dateString) {
            const date = new Date(dateString);
            return date.toLocaleString();
        },

        audioToggleMetadata(audioId) {
            this.audioExpandedMetadata = this.audioExpandedMetadata === audioId ? '' : audioId;
        },

        audioFormatDuration(seconds) {
            if (!seconds || seconds === 0) return 'Unknown';
            const hours = Math.floor(seconds / 3600);
            const mins = Math.floor((seconds % 3600) / 60);
            const secs = Math.floor(seconds % 60);

            if (hours > 0) {
                return `${hours}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
            }
            return `${mins}:${secs.toString().padStart(2, '0')}`;
        },

        audioFormatFileSize(bytes) {
            if (!bytes || bytes === 0) return 'Unknown';
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(1024));
            return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${sizes[i]}`;
        },

    };
}